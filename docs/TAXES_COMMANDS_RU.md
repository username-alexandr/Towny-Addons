# NeverLandTownyTaxes — команды

## Город

- `/t taxes` — состояние казначейства;
- `/t taxes policies` — политики города;
- `/t taxes create <fixed|income|transaction> <значение> [интервал_минут]`;
- `/t taxes remove <ID>`;
- `/t taxes debt` и `/t taxes pay`;
- `/t sanctions`;
- `/t sanctions impose <город> <trade_block|command_block|tax_multiplier> <значение> <часы> <причина>`;
- `/t sanctions remove <ID>`;
- `/t agreements propose <город> <tax_exemption|trade_preference|sanction_relief> <значение> [часы]`;
- `/t agreements accept|reject|cancel <ID>`.

## Государство

- `/n taxes create|remove ...`;
- `/n sanctions impose <town|nation> <цель> <эффект> <значение> <часы> <причина>`;
- `/n sanctions remove <ID>`;
- `/n agreements propose <нация> <тип> <значение> [часы]`;
- `/n agreements accept|reject|cancel <ID>`.

## Администратор

- `/townytaxes policy create <global|nation|town|player> <цель> <fixed|income|transaction> <значение> <получатель_scope> <получатель> [интервал]`;
- `/townytaxes policy remove <ID>`;
- `/townytaxes sanction add <scope> <цель> <эффект> <значение> <часы> <причина>`;
- `/townytaxes sanction remove <ID>`;
- `/townytaxes agreement add <scope1> <сторона1> <scope2> <сторона2> <тип> <значение> <часы>`;
- `/townytaxes bank`, `/townytaxes ledger [страница]`, `/townytaxes collect`, `/townytaxes reload`.

Для `global` имя цели и получателя можно указать как `server`. Нулевое количество часов означает бессрочную санкцию или соглашение.

Примеры:

```text
/t taxes create fixed 50 1440
/t taxes create income 5
/n sanctions impose town Rivendell trade_block 1 72 Нарушение торгового договора
/townytaxes policy create global server income 3 global server 1440
```
