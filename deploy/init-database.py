"""DB 초기 비밀번호를 metadata Identity로 직접 조회한다. 값은 파일/인자/로그에 남기지 않는다."""
import base64
import json
import os
import re
import subprocess
import urllib.request


def get_json(url, headers):
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=10) as response:
        return json.load(response)


def password():
    if os.environ.get("DB_BOOTSTRAP_MODE") == "local-smoke":
        return "decision_assistant"  # 폐기 가능한 로컬 검증 DB 전용
    if os.environ.get("DB_BOOTSTRAP_MODE") != "gcp":
        raise ValueError("DB_BOOTSTRAP_MODE_REQUIRED")
    project = os.environ["PROD_PROJECT_ID"]
    version = os.environ["DB_SECRET_VERSION"]
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,61}[a-z0-9]", project) or not re.fullmatch(r"[1-9][0-9]*", version):
        raise ValueError("INVALID_SECRET_REFERENCE")
    token = get_json("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token",
                     {"Metadata-Flavor": "Google"})["access_token"]
    secret = get_json(f"https://secretmanager.googleapis.com/v1/projects/{project}/secrets/db-password/versions/{version}:access",
                      {"Authorization": f"Bearer {token}"})
    value = base64.b64decode(secret["payload"]["data"], validate=True).decode("utf-8")
    if not value or "\x00" in value:
        raise ValueError("INVALID_DATABASE_PASSWORD")
    return value


def main():
    # Docker 임시 초기화 서버는 Unix socket만 연다. 본 서버도 고정 hba_file의 scram 정책을 사용한다.
    value = password().replace("'", "''")
    sql = "SET standard_conforming_strings = on;\n" + f"CREATE ROLE decision_assistant LOGIN PASSWORD '{value}';\n" + "ALTER DATABASE decision_assistant OWNER TO decision_assistant;\n"
    result = subprocess.run(["psql", "-U", "postgres", "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-q"],
                            input=sql, text=True, capture_output=True, timeout=20)
    if result.returncode:
        raise RuntimeError("DATABASE_INITIALIZATION_FAILED")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # urllib/psql 예외에 인증값이나 SQL이 포함될 수 있으므로 원문을 출력하지 않는다.
        raise SystemExit("DATABASE_INITIALIZATION_FAILED") from None
