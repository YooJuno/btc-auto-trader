# PostgreSQL DB 사용법 (초간단 매뉴얼)

이 문서는 PostgreSQL에 처음 접근하는 사용자를 위한 기본 가이드입니다.

---

## 1) PostgreSQL 접속하기
로컬 서버에 접속:
```bash
sudo -i -u postgres psql
```

접속 종료:
```sql
\q
```

---

## 2) 계정(유저) 만들기
```sql
CREATE USER myuser WITH PASSWORD 'mypassword';
```

---

## 3) DB 만들기
```sql
CREATE DATABASE mydb OWNER myuser;
```

---

## 4) 권한 부여
```sql
GRANT ALL PRIVILEGES ON DATABASE mydb TO myuser;
```

---

## 5) 만든 DB로 접속
psql 안에서:
```sql
\c mydb
```

터미널에서 바로:
```bash
psql -U myuser -d mydb -h localhost
```

---

## 6) 테이블 만들기 (예시)
```sql
CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);
```

---

## 7) 기본 SQL 문법
데이터 넣기:
```sql
INSERT INTO users (name) VALUES ('Alice');
```

조회:
```sql
SELECT * FROM users;
```

조건 조회:
```sql
SELECT * FROM users WHERE name = 'Alice';
```

수정:
```sql
UPDATE users SET name = 'Bob' WHERE id = 1;
```

삭제:
```sql
DELETE FROM users WHERE id = 1;
```

---

## 8) 자주 쓰는 psql 명령어
```sql
\l        -- DB 목록
\du       -- 유저 목록
\dt       -- 테이블 목록
\d users  -- 테이블 구조 보기
```

