# PostgreSQL DB 사용법

## postgres DB 접속 (관리용 DB)
```bash
sudo -i -u postgres psql
```

---

## 유저 및 DB 생성
```sql
CREATE USER myuser WITH PASSWORD 'password'; -- 계정(유저) 만들기
CREATE DATABASE mydb OWNER myuser; -- DB 만들기 및 소유자 지정
GRANT ALL PRIVILEGES ON DATABASE mydb TO myuser; -- 권한 부여
```

```bash
psql -h localhost -p 5432 -U juno -d "btc-auto-trader"
```

---

## 테이블 만들기 및 삭제
```sql
CREATE TABLE <table> (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);
```
```sql
DROP TABLE accounts, markets, orders, positions;
```
---

## 기본 SQL 문법
```sql
INSERT INTO <table> (name, age, email) VALUES ('Alice', 30, 'alice@example.com'); --- 삽입
SELECT * FROM <table>; --- 조회
SELECT * FROM <table> WHERE name = 'Alice'; --- 조건 조회
UPDATE <table> SET name = 'Bob' WHERE id = 1; --- 수정
DELETE FROM <table> WHERE id = 1; --- 삭제
```

---

## 터미널에서 DB로 들어오는 값 확인
```bash
watch -n 1 "psql -h localhost -p 5432 -U juno -d btc-auto-trader -c 'SELECT * FROM strategy_config'"
```
---

## 자주 쓰는 psql 명령어
```sql
\q          -- 종료
\l          -- DB 목록
\du         -- 유저 목록
\dt         -- 테이블 목록
\d <table>  -- 테이블 구조 보기
\c <mydb>   -- 접속 DB 변경
```

## USER
- superuser : postgres


## 도커 포트 바꾸기
```bash
sudo systemctl stop postgresql
sudo sed -i "s/^#\\?port = .*/port = 5432/" /etc/postgresql/16/main/postgresql.conf
sudo systemctl start postgresql
```

## systemd
```bash
sudo systemctl status postgresql
sudo systemctl stop postgresql
sudo systemctl start postgresql
sudo systemctl restart postgresql
```

--- 

## 클러스터?
- 하나의 서버 인스턴스(= 하나의 데이터 디렉터리 + 설정 + 여러 DB) 묶음
- PostgreSQL은 여러 클러스터를 가질 수 있음
- 클러스터 관리 명령어: `pg_lsclusters`, `pg_createcluster`, `pg_dropcluster`, `pg_ctlcluster`
- 기본 클러스터 위치: `/var/lib/postgresql/<버전>/main`
ex) 
Ver Cluster Port Status Owner    Data directory
16  main    5433 online postgres /var/lib/postgresql/16/main

---