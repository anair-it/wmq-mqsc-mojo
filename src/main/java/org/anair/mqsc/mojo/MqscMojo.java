package org.anair.mqsc.mojo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.ConfigurationNode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Generate IBM Websphere MQSC files that can define/alter queue definitions.
 * <p>
 * <ol>
 *  <li>Reads environment specific properties from mq_env_config.xml. This file will have environment specific values for MQSC attributes.</li>
 *  <li>Read all MQSC files in src/main/resources.</li>
 *  <li>Apply environment specific changes to the script</li>
 *  <li>Generate a combined MQSC file that will have all scripts in one file per environment</li>
 *  <li>Generate a combined MQSC file that will have scripts per release folder per environment</li>
 * </ol>
 * <p>
 * Generated files will be in target/generated_mqsc
 * 
 * <p>
 * Plugin configuration:
 * <pre>{@code
 		<plugin>
			<groupId>org.anair.maven.mojo</groupId>
			<artifactId>wmq-mqsc-mojo</artifactId>
			<version>0.0.1</version>
			<executions>
				<execution>
					<phase>package</phase>
					<goals>
						<goal>mqsc</goal>
					</goals>
				</execution>
			</executions>
		</plugin>
 * }</pre>
 * 
 * @author Anoop Nair
 *
 */
@Mojo(name = "mqsc")
public class MqscMojo extends AbstractMojo {

	private static final Logger LOG = LoggerFactory.getLogger(MqscMojo.class);
	
	private static final String MQSC_ALL_FILE_PREFIX = "all";
	private static final String MQSC_FILE_EXTENSION = ".mqsc";
	private static final String MQSC_SOURCE_DIR = "src/main/resources";
	private static final String MQSC_GEN_DIR = "target/generated_mqsc";
	private static final String MQSC_VAR_PREFIX = "${";
	private static final String MQSC_VAR_SUFFIX = "}";
	private static final String MQ_ENV_CONFIG_FILE = MQSC_SOURCE_DIR+"/mq_env_config.xml";

	/**
	 * MQ properties with environment specific setting 
	 */
	@Parameter(property = "mq_environment_config", defaultValue=MQ_ENV_CONFIG_FILE, readonly=true)
    private String mqEnvironmentConfig;
	
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			XMLConfiguration config = new XMLConfiguration(mqEnvironmentConfig);
			FileUtils.deleteQuietly(new File(MQSC_GEN_DIR));
			
			combineAllMqscFiles(config);
			mqscFilesForARelease(config);
		} catch (ConfigurationException e) {
			LOG.error(e.getMessage(), e);
		}
		LOG.debug("Generated MQSC files.");
	}

	private void mqscFilesForARelease(XMLConfiguration config) {
		String[] releaseDirectories = getMqscReleaseDirectories();
		
		for(String releaseDirectory: releaseDirectories) {
			List<File> mqscFilesInAReleaseDirectory = getMqscFilesInADirectory(releaseDirectory);
			processMqscFiles(config, mqscFilesInAReleaseDirectory, releaseDirectory);
			LOG.debug("Processed MQSC files in " + releaseDirectory + " release directory");
		}
	}

	private void combineAllMqscFiles(XMLConfiguration config) {
		List<File> allMqscFiles = getAllMqscFiles();
		
		processMqscFiles(config, allMqscFiles, MQSC_ALL_FILE_PREFIX);
		LOG.debug("Processed MQSC files in all release directories");
	}

	private void processMqscFiles(XMLConfiguration config, List<File> mqscFiles, String releaseFolder) {
		
		if(CollectionUtils.isNotEmpty(mqscFiles)){
			List<ConfigurationNode> allMQSCEnvironments = config.getRootNode().getChildren();
			if(CollectionUtils.isNotEmpty(allMQSCEnvironments)){
				MultiValuedMap<String,String> allMQSCForEnvironment = new ArrayListValuedHashMap<>();
				
				processMQSCForAllEnvironments(config, mqscFiles,
						allMQSCEnvironments, allMQSCForEnvironment);
				
				for(String key: allMQSCForEnvironment.keySet()){
					List<String> mqscContentList = (List<String>)allMQSCForEnvironment.get(key);
					generateMQSCContent(config, mqscContentList, key, releaseFolder);
				}
			}
		}
	}

	private void generateMQSCContent(XMLConfiguration config, List<String> mqscContents, String environment, String releaseFolder) {
		File combinedFile = createMqscFile(environment, releaseFolder);
		
		String combinedContent = StringUtils.join(mqscContents, "\r\n");
		String updatedFileContent = processMqscFile(config, combinedContent, environment);
		
		try {
			FileUtils.writeStringToFile(combinedFile, updatedFileContent, Charset.defaultCharset());
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
	}
	
	private static File createMqscFile(String environment, String releaseFolder) {
		String mqscFileName = MQSC_GEN_DIR+"/"+environment+"/"+releaseFolder+"-"+environment+MQSC_FILE_EXTENSION;
		return new File(mqscFileName);
	}

	private void processMQSCForAllEnvironments(XMLConfiguration config,
			List<File> allMqscFiles,
			List<ConfigurationNode> allMQSCEnvironments,
			MultiValuedMap<String,String> allMQSCForEnvironment) {
		for(ConfigurationNode rootConfigNode: allMQSCEnvironments){
			String environment = rootConfigNode.getName();
			
			for(File mqscFile: allMqscFiles){
				try {
					String originalfileContent = FileUtils.readFileToString(mqscFile, Charset.defaultCharset());
					allMQSCForEnvironment.put(environment, originalfileContent);
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			}
		}
	}

	private String processMqscFile(XMLConfiguration config, String fileContent, String environment) {
		if(StringUtils.isNotBlank(fileContent)){
			List<String> mqscVars = new ArrayList<>();
			List<String> mqscVarValues = new ArrayList<>();
			
			removeInvalidCharacters(fileContent);
			
			extractMQSCAttributeAndValueForReplacement(config,
					environment, mqscVars, mqscVarValues);
			
			String updatedFileContent = decorateMQSC(fileContent,
					mqscVars, mqscVarValues);
			return updatedFileContent;
		}
		return null;
	}

	private void extractMQSCAttributeAndValueForReplacement(
			XMLConfiguration config, String environment, List<String> mqscVars,
			List<String> mqscVarValues) {
		SubnodeConfiguration localSubnodeConfig = config.configurationAt(environment);
		
		for(Iterator<String> iter = localSubnodeConfig.getKeys();iter.hasNext();){
			String key = iter.next();
			mqscVars.add(MQSC_VAR_PREFIX + key + MQSC_VAR_SUFFIX);
			mqscVarValues.add(config.getString(environment + "." + key));
		}
	}

	private String decorateMQSC(String fileContent,
			List<String> mqscVars, List<String> mqscVarValues) {
		if(CollectionUtils.isNotEmpty(mqscVars)){
			String updatedFileContent = StringUtils.replaceEach(fileContent
					, mqscVars.toArray(new String[mqscVars.size()])
					, mqscVarValues.toArray(new String[mqscVarValues.size()]));
			return updatedFileContent;
		}
		return null;
	}
	
	private List<File> getAllMqscFiles() {
		return (List<File>) getMqscFilesInADirectory("");
	}
	
	private List<File> getMqscFilesInADirectory(String dir) {
		return (List<File>) FileUtils.listFiles(new File(MQSC_SOURCE_DIR+"/"+dir), new SuffixFileFilter(MQSC_FILE_EXTENSION), TrueFileFilter.INSTANCE);
	}
	
	private String[] getMqscReleaseDirectories() {
		File dir = new File(MQSC_SOURCE_DIR);
		return dir.list(DirectoryFileFilter.INSTANCE);
	}
	
	private void removeInvalidCharacters(String fileContent) {
		if(StringUtils.isNotBlank(fileContent)){
			fileContent = fileContent.replaceAll("\\*(.*)\\+\r\n", ""); // Remove comments in the middle of MQSC script
			fileContent = StringUtils.remove(fileContent, "\t"); // Remove tabs. Tab is an invalid character in a MQSC script.
			fileContent = fileContent.replaceAll("\r\n(\\s)*\r\n", "\r\n"); //Remove empty lines
			fileContent = fileContent.replaceAll("\\s+", " "); //Remove unwanted whitespace
		}
	}

	public void setMqEnvironmentConfig(String mqEnvironmentConfig) {
		this.mqEnvironmentConfig = mqEnvironmentConfig;
	}
}
