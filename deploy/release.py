"""단일 VM에 전달할 이미지 아카이브를 만들고 검증한다."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile


PLATFORM = "linux/amd64"
BASES = {
    "frontend": "node:24-alpine",
    "backend": "eclipse-temurin:21-jre-jammy",
    "postgres": "postgres:17-bookworm",
}
CADDY = "docker.io/library/caddy@sha256:fc9cbc100585871d18d4e451cca7021785c90a7006087427ae5ceb3e4efe6424"
SERVICES = (*BASES, "proxy")
DEPLOY_FILES = ("deploy/compose.yaml", "deploy/compose.release.yaml", "deploy/Caddyfile")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")


def run(*args, cwd=None):
    subprocess.run(args, cwd=cwd, check=True)


def output(*args, cwd=None):
    return subprocess.check_output(args, cwd=cwd, encoding="utf-8").strip()


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def inspect(ref):
    image, = json.loads(output("docker", "image", "inspect", ref))
    if f"{image['Os']}/{image['Architecture']}" != PLATFORM:
        raise ValueError(f"지원하지 않는 이미지 플랫폼: {ref}")
    return image


def image_tag(service, commit):
    return f"decision-assistant-release/{service}:{commit}"


def build(bundle, commit):
    repo = Path(__file__).resolve().parent.parent
    if not COMMIT.fullmatch(commit) or output("git", "rev-parse", "HEAD", cwd=repo) != commit:
        raise ValueError("현재 checkout과 일치하는 전체 Git SHA가 필요합니다.")
    bundle.mkdir(parents=True)
    with tempfile.TemporaryDirectory() as directory:
        context = Path(directory)
        source = context / "source.tar"
        run("git", "archive", "--format=tar", f"--output={source}", commit, cwd=repo)
        source_digest = sha256(source)
        with tarfile.open(source) as archive:
            archive.extractall(context, filter="data")
        source.unlink()
        run("sh", "backend/gradlew", "-p", "backend", "bootJar", cwd=context)
        jar, = (context / "backend/build/libs").glob("decision-assistant-api-*-SNAPSHOT.jar")
        jar_digest = sha256(jar)
        deploy_files = {name: sha256(context / name) for name in DEPLOY_FILES}
        images = {}
        for service, base in BASES.items():
            run("docker", "pull", "--platform", PLATFORM, base)
            pinned_base = inspect(base)["RepoDigests"][0]
            dockerfile = f"deploy/{service}.Dockerfile"
            tag = image_tag(service, commit)
            run("docker", "build", "--platform", PLATFORM, "--build-arg", f"BASE_IMAGE={pinned_base}",
                "--label", f"org.opencontainers.image.revision={commit}", "-t", tag, "-f", dockerfile, ".", cwd=context)
            images[service] = {"tag": tag, "id": inspect(tag)["Id"], "source": dockerfile, "base": pinned_base}
        run("docker", "pull", "--platform", PLATFORM, CADDY)
        tag = image_tag("proxy", commit)
        run("docker", "tag", CADDY, tag)
        images["proxy"] = {"tag": tag, "id": inspect(tag)["Id"], "source": CADDY}
        run("docker", "image", "save", "-o", str(bundle / "images.tar"), *(item["tag"] for item in images.values()))
    record = {
        "commit": commit, "platform": PLATFORM, "source_sha256": source_digest,
        "backend_jar_sha256": jar_digest, "archive_sha256": sha256(bundle / "images.tar"),
        "deploy_files": deploy_files, "images": images,
    }
    (bundle / "release.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    (bundle / "images.env").write_text(
        "".join(f"RELEASE_{service.upper()}_IMAGE={item['tag']}\n" for service, item in images.items()),
        encoding="utf-8",
    )


def load_release(bundle):
    record = json.loads((bundle / "release.json").read_text(encoding="utf-8"))
    commit = record["commit"]
    if not COMMIT.fullmatch(commit) or record["platform"] != PLATFORM or set(record["images"]) != set(SERVICES):
        raise ValueError("릴리스 기록의 커밋, 플랫폼 또는 서비스 목록이 올바르지 않습니다.")
    for service, item in record["images"].items():
        if item["tag"] != image_tag(service, commit) or not DIGEST.fullmatch(item["id"]):
            raise ValueError(f"릴리스 이미지 참조가 올바르지 않습니다: {service}")
    return record


def verify(bundle, runtime_env, local_smoke, running):
    record = load_release(bundle)
    repo = Path(__file__).resolve().parent.parent
    for name in DEPLOY_FILES:
        if sha256(repo / name) != record["deploy_files"][name]:
            raise ValueError(f"릴리스 배포 파일 불일치: {name}")
    compose = [
        "docker", "compose", "--env-file", str(runtime_env), "--env-file", str(bundle / "images.env"),
        "-f", "deploy/compose.yaml", "-f", "deploy/compose.release.yaml",
    ]
    if local_smoke:
        compose.extend(["-f", "deploy/compose.local.yaml", "-p", "decision-mvp-smoke"])
    config = json.loads(output(*compose, "config", "--format", "json", cwd=repo))["services"]
    if set(config) != set(SERVICES):
        raise ValueError("릴리스와 Compose 서비스 목록이 일치하지 않습니다.")
    for service, item in record["images"].items():
        settings = config[service]
        if settings.get("build") or settings.get("pull_policy") != "never" or settings.get("image") != item["tag"]:
            raise ValueError(f"Compose 이미지·빌드·pull 설정 불일치: {service}")
        image = inspect(item["tag"])
        if image["Id"] != item["id"]:
            raise ValueError(f"릴리스 이미지 ID 불일치: {service}")
        if service in BASES and (image["Config"].get("Labels") or {}).get("org.opencontainers.image.revision") != record["commit"]:
            raise ValueError(f"릴리스 이미지 Git SHA 불일치: {service}")
        if running:
            container = output(*compose, "ps", "-q", service, cwd=repo)
            if not container or output("docker", "container", "inspect", "--format", "{{.Image}}", container) != item["id"]:
                raise ValueError(f"실행 컨테이너의 이미지 ID 불일치: {service}")
    print(f"릴리스 이미지 검증 완료: {record['commit']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("build", "load", "verify"):
        action = commands.add_parser(name)
        action.add_argument("bundle", type=Path)
        if name == "build":
            action.add_argument("commit")
        if name == "verify":
            action.add_argument("runtime_env", type=Path)
            action.add_argument("--local-smoke", action="store_true")
            action.add_argument("--running", action="store_true")
    args = parser.parse_args()
    bundle = args.bundle.resolve()
    try:
        if args.command == "build":
            build(bundle, args.commit)
        elif args.command == "load":
            record = load_release(bundle)
            archive = bundle / "images.tar"
            if sha256(archive) != record["archive_sha256"]:
                raise ValueError("릴리스 이미지 아카이브 SHA-256 불일치")
            run("docker", "image", "load", "-i", str(archive))
        else:
            verify(bundle, args.runtime_env.resolve(), args.local_smoke, args.running)
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"릴리스 {args.command} 실패: {error}\n")


if __name__ == "__main__":
    main()
