# Адаптер NeverLandPassport 0.4.0

Патч применяется к корню исходного комплекта NeverLandPassport 0.3.0 (где находится папка source):

```sh
git apply --unidiff-zero NeverLandPassport-0.3.0-to-0.4.0.patch
cd source
mvn clean package
```

Для полной сборки нужны зависимости из pom.xml и Java 25. Патч добавляет только публичный Citizens API, обновляет отображение гражданства и защищает от редактирования параллельного текстового поля при установленном Citizens. SQLite, предметы и ресурс-пак не изменяются.
