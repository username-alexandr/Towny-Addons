# Headless city card renderer

Run `python3 run.py --java-home <JDK25> --minecraft <original-26.2-client.jar> --gson <gson.jar> --output <temporary-output>`.

Uses production CityCardButton, FlatButton and CityDashboardLayout. A small AWT adapter implements drawing and loads bitmap glyphs from the supplied genuine Minecraft JAR. It produces 16 images and checks clipped mouse regions. It does not launch the game or emulate all screen lifecycle, focus, tooltips or renderer behaviour. Preview records are examples, not live town state. No test adapter is shipped in the production JAR.
