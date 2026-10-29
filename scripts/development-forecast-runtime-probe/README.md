# Native development forecast probe

Use a fresh marked loopback-only Folia fixture. Actual Towny, Builds, Resources, Upkeep, Power and Policies JARs are loaded. Sites, custom tariffs/generation and one MONEY_PENDING invoice are explicit fixture seeds, not player construction or a real bank transaction. The Policies default option is explicitly configured to add 50% upkeep for the two tested buildings. Production providers and settings parsing perform the forecast.

```bash
python scripts/development-forecast-runtime-probe/prepare.py --template /abs/stopped-template --server /abs/fresh-server --towny /abs/Towny.jar
python scripts/development-forecast-runtime-probe/build.py --server-dir /abs/fresh-server --java-home /abs/jdk25 --annotations /abs/annotations.jar
python scripts/development-forecast-runtime-probe/run.py --server /abs/fresh-server --java-home /abs/jdk25
```

Both JVMs run serially. After providers finish startup, this fixture cancels only their periodic tasks so repeated forecast calls can be checked against exactly unchanged invoices, paid clock and charge. Battery charge/discharge rates are zero by fixture configuration. Actual mayor/claims and site metadata remain live. Administrative freeze is tested explicitly and thawed immediately. The second JVM checks saved pending invoice, suspension and battery identity after a full restart.

No GUI client, bank payment or actual connection of a future site is claimed. New sites are clearly conditional; existing foreign or unmaintained sites never become guaranteed generation. Existing legacy synchronous Upkeep IO is outside this read-only change.

Проверенный стенд: folia-development-forecast-4, 18+5 PASS. Нейтральный ordinary не изменяется: в fixture создаётся отдельный режим forecast и сохранённый принятый выбор города. Первый/повторный запуск можно выполнять отдельно через --phase first/restart.
