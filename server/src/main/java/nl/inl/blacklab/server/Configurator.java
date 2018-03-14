/*
 * Copyright 2018 INL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.inl.blacklab.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServletResponse;
import nl.inl.blacklab.search.ConfigReader;
import static nl.inl.blacklab.server.BlackLabServer.CONFIG_ENCODING;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.util.FileUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 *
 * @author eduard
 */
public class Configurator implements ServletContextListener {

    private static final Logger logger = LogManager.getLogger(Configurator.class);

    private static JsonNode searchConfig;
    
    private static final List<File> searchDirs = new ArrayList<>();
    

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            readConfig(sce.getServletContext());
        } catch (BlsException ex) {
            logger.error(ex);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }

    private void readConfig(ServletContext context) throws BlsException {
        try {
            // load blacklab's internal config before doing anything
            // we will later overwrite some settings from our own config
            // It's important we do this as early as possible as some things are loaded depending on the config (such as plugins)
            try {
                ConfigReader.loadDefaultConfig();
            } catch (Exception e) {
                throw new BlsException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error reading BlackLab configuration file", e);
            }

            File servletPath = new File(context.getRealPath("."));
            logger.debug("Running from dir: " + servletPath);

            String configFileName = "blacklab-server";
            List<String> exts = Arrays.asList("json", "yaml", "yml");

            searchDirs.add(servletPath.getAbsoluteFile().getParentFile().getCanonicalFile());
            searchDirs.addAll(ConfigReader.getDefaultConfigDirs());

            Reader configFileReader = null;
            boolean configFileIsJson = false;

            File configFile = FileUtil.findFile(searchDirs, configFileName, exts);
            if (configFile != null && configFile.canRead()) {
                logger.debug("Reading configuration file " + configFile);
                configFileReader = FileUtil.openForReading(configFile, CONFIG_ENCODING);
                configFileIsJson = configFile.getName().endsWith(".json");
            }

            if (configFileReader == null) {
                logger.debug(configFileName + ".(json|yaml) not found in webapps dir; searching classpath...");

                for (String ext : exts) {
                    InputStream is = getClass().getClassLoader().getResourceAsStream(configFileName + "." + ext);
                    if (is == null) {
                        continue;
                    }

                    logger.debug("Reading configuration file from classpath: " + configFileName);
                    configFileReader = new InputStreamReader(is, CONFIG_ENCODING);
                    configFileIsJson = ext.equals("json");
                    break;
                }
            }

            if (configFileReader == null) {
                String descDirs = StringUtils.join(searchDirs, ", ");
                throw new ConfigurationException("Couldn't find blacklab-server.(json|yaml) in dirs " + descDirs + ", or on classpath. Please place "
                        + "blacklab-server.json in one of these locations containing at least the following:\n"
                        + "{\n"
                        + "  \"indexCollections\": [\n"
                        + "    \"/my/indices\" \n"
                        + "  ]\n"
                        + "}\n\n"
                        + "With this configuration, one index could be in /my/indices/my-first-index/, for example.. For additional documentation, please see http://inl.github.io/BlackLab/");
            }

        } catch (JsonProcessingException e) {
            throw new ConfigurationException("Invalid JSON in configuration file", e);
        } catch (IOException e) {
            throw new ConfigurationException("Error reading configuration file", e);
        }
    }

    public static JsonNode getSearchConfig() throws ConfigurationException {
        if (searchConfig==null) {
                String descDirs = StringUtils.join(searchDirs, ", ");
                throw new ConfigurationException("Couldn't find blacklab-server.(json|yaml) in dirs " + descDirs + ", or on classpath. Please place "
                        + "blacklab-server.json in one of these locations containing at least the following:\n"
                        + "{\n"
                        + "  \"indexCollections\": [\n"
                        + "    \"/my/indices\" \n"
                        + "  ]\n"
                        + "}\n\n"
                        + "With this configuration, one index could be in /my/indices/my-first-index/, for example.. For additional documentation, please see http://inl.github.io/BlackLab/");
        }
        return searchConfig;
    }
    
    
}
