
## How to run

Terminal #1

```sh
$ sbt "runMain akkabank.Main cassandra"
```
It will start the Cassandra on Port 9042

Terminal #2

```sh
$ sbt "runMain akkabank.Main"
```
It will start two nodes on ports 2551 and 2552, and two http servers on port 8051 and 8052


## How to test
Since there are two http ports (8051 and 8052) running, you can pick one of them.
```sh
# open a bank account
$ curl -X POST \
  http://localhost:8051/accounts \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{ "name": "Sam" }'
{"accountName":"Sam","balance":{"amount":0,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}

# retrieve a bank account
$ curl -X GET \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7 \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json'
{"accountName":"Sam","balance":{"amount":0,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}

# update the bank account name
$ curl -X PUT \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7 \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{ "name": "Joe" }'
{"accountName":"Joe","balance":{"amount":0,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}


# deposit
$ curl -X POST \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7/events \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{ "type": "deposit", "amount": 40.32 }'
{"accountName":"Joe","balance":{"amount":40.32,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}


# withdraw
$ curl -X POST \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7/events \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{ "type": "withdraw", "amount": 5.00 }'
{"accountName":"Joe","balance":{"amount":35.32,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}

# close a bank account
$ curl -X DELETE \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7 \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json'
{"accountName":"Joe","balance":{"amount":35.32,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}

```
