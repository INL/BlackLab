# Plugins

- Create a class implementing `ConvertPlugin` or `TagPlugin`
- Make the class known to the java [SPI](https://docs.oracle.com/javase/tutorial/sound/SPI-intro.html) system.  
In short:
    - Create a jar containing your plugin class.
    - Add a file to the jar under `/META-INF/services/` with the name `nl.inl.blacklab.indexers.preprocess.ConvertPlugin` or `nl.inl.blacklab.indexers.preprocess.TagPlugin` depending on your plugin's type.
    - Add a single line containing your class's fully-qualified class name.
    - Add your jar to BlackLab's classpath.

Configuring your plugin is possible through `blacklab.json`.  
Any options under `plugins.yourPluginId` will be passed to your plugin when it's initialized.

If your plugin was loaded successfully it can now be used by adding the following to an import format:

    tagplugin: yourPluginId
    convertPlugin: yourPluginId