IBM WebSphere MQSC file generator
===============================

This is a maven mojo to generate MQSC files. MQSC is the IBM MQ scripting language. Any Websphere MQ object can be created/altered using a MQSC script.        

[MQSC reference](http://www-01.ibm.com/support/knowledgecenter/SSFKSJ_7.0.1/com.ibm.mq.csqzaj.doc/sc10340_.htm?lang=en)


Software Prerequisites
----------------------
1. JDK 8
2. Maven 3+


Example project
-----
[wmq-mqsc-example](https://github.com/anair-it/wmq-mqsc-example)


Setup
---
1. Create a maven project that will house your MQSC scripts
	- MQSC script files should be created in src/main/resources                     
2. Add the maven mojo as a plugin:
	
		<plugin>
			<groupId>org.anair.maven.mojo</groupId>
			<artifactId>wmq-mqsc-mojo</artifactId>
			<version>0.0.2</version>
			<executions>
				<execution>
					<phase>package</phase>
					<goals>
						<goal>mqsc</goal>
					</goals>
				</execution>
			</executions>
		</plugin>
	
3. Create "src/main/resources/mq\_env\_config.xml". Define environment specific values that need to be updated in the generated MQSC file. Sample content:      
	
		<mqsc>
		    <local>
		    	<DEFPSIST>YES</DEFPSIST>
		    </local>
		    
		    <dev>
		    	<DEFPSIST>NO</DEFPSIST>
		    </dev>
		    
		    <prod>
		    	<DEFPSIST>YES</DEFPSIST>
		    </prod>
		</mqsc>
4.Create release folder like "000", "001" etc in src/main/resources       
   - 000 folder will be the base folder to create initial MQ objects    
   - 001 folder will house scripts for the next release   

5.Create MQSC script file with extension as ".mqsc" in 000 folder. Add MQSC scripts to the file
   - Change DEFPSIST configuration to DEFPSIST(${DEFPSIST}). This will get replaced with the value in mq\_env\_config.xml         

	
Generate MQSC files
----------
1. Run    
	
		mvn clean package     
2.Generated files will be at target/generated_mqsc	        
3.Sequence of steps processed:      
	- Reads environment specific properties from mq\_env\_config.xml. This file will have environment specific values for MQSC attributes.       
	- Read all MQSC files in src/main/resources.      
	- Apply environment specific changes to the script      
	- Generate a combined MQSC file that will have all scripts in one file per environment     
	- Generate a combined MQSC file that will have scripts per release folder per environment       
