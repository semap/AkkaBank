# Akka Typed Example

- Akka Typed 2.6.3
- Akka Http
- Akka Cluster Sharding
- Akka Persistence
- Event Sourcing
- Scala 2.13.1

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

Terminal #3, #4, #5... (Optional)

```sh
$ sbt "runMain akkabank.Main 2553"
```
```sh
$ sbt "runMain akkabank.Main 2554"
```
```sh
$ sbt "runMain akkabank.Main 2555"
```
It will start one node on ports 2553, and a http server on port 8053. (If you specify the port `xxyy`, the http port will be `80yy`)


## How to test
Since there are two http ports (8051 and 8052) running, you can pick one of them.
### Open a bank account
```sh
$ curl -X POST \
  http://localhost:8051/accounts \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{ "name": "Sam" }'

{"accountName":"Sam","balance":{"amount":0,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}
```
### Retrieve a bank account
```sh
$ curl -X GET \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7 \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json'

{"accountName":"Sam","balance":{"amount":0,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}
```
### Update the bank account name
```sh
$ curl -X PUT \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7 \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{ "name": "Joe" }'

{"accountName":"Joe","balance":{"amount":0,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}
```
### Deposit
```sh
$ curl -X POST \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7/events \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{ "type": "deposit", "amount": 40.32 }'

{"accountName":"Joe","balance":{"amount":40.32,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}
```
### Withdraw
```sh
$ curl -X POST \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7/events \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{ "type": "withdraw", "amount": 5.00 }'

{"accountName":"Joe","balance":{"amount":35.32,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}
```
### Close a bank account
Once the account is deleted, you can only use GET. (No Deposit, Withdraw, Update...)
```sh
$ curl -X DELETE \
  http://localhost:8051/accounts/ea3acd0a-9fbd-4827-9c5f-026499f701e7 \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json'

{"accountName":"Joe","balance":{"amount":35.32,"currency":"USD"},"id":"ea3acd0a-9fbd-4827-9c5f-026499f701e7"}
```
