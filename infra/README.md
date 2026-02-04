```bash
sudo apt install postgresql postgresql-contrib
```

```bash
sudo -i -u postgres psql
```

### 실행/상태 확인
```bash
sudo systemctl start postgresql
sudo systemctl enable postgresql
sudo systemctl status postgresql
```

```sql
ALTER USER postgres WITH PASSWORD '';
\q
```

### 설명
- 비트코인 자동매매프로그램
- 리액트 + 스프링부트 + PostgreSQL
- UPBIT API 사용(추후 선물, 주식 으로의 확장까지 고려)
- 사용자에게 매매 자유도를 높이는것이 목적
- 장벽없는 매매 경험을 제공
- 종목추천부터 매매전략(스켈핑, 스윙 등) 추천
- 사용자 맞춤 지표 제공 등을 통해 수익률 제공이 목표
