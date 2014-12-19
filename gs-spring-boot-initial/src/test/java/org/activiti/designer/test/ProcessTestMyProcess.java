package org.activiti.designer.test;

import static org.junit.Assert.*;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.io.FileInputStream;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.test.ActivitiRule;
import org.junit.Rule;
import org.junit.Test;

// Import this so process engine can be started as a Spring Bean
import org.activiti.spring.ProcessEngineFactoryBean;

public class ProcessTestMyProcess {

	private String filename = "F:/My Work/Generalli/STS/STS Workspace/gs-spring-boot-initial/src/main/resources/diagrams/CustomerComplaintProcess.bpmn";

	@Rule
	public ActivitiRule activitiRule = new ActivitiRule();

	@Test
	public void startProcess() throws Exception {
		
		// Initialize repository service: for storing, retrieving business processes from the repository
		// RepositoryService is initialized from ActivitiRule
		RepositoryService repositoryService = activitiRule.getRepositoryService();
		repositoryService.createDeployment().addInputStream("customerComplaintProcess.bpmn20.xml",
				new FileInputStream(filename)).deploy();
		
		// Initialize runtime service: for starting business processes, fetching information about process in execution 
		// Runtime service is initialized from ActivitiRule 
		RuntimeService runtimeService = activitiRule.getRuntimeService();
		
		Map<String, Object> variableMap = new HashMap<String, Object>();
		variableMap.put("name", "Activiti");
		
		variableMap.put("customerName", "Thong Huynh");
		
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("customerComplaintProcess", variableMap);
		
		
		// Testing data
		assertNotNull(processInstance.getId());
		System.out.println("id " + processInstance.getId() + " "
				+ processInstance.getProcessDefinitionId());
		
		System.out.println("check variables");
		System.out.println(processInstance.getActivityId());
		System.out.println(processInstance.getName());
		System.out.println(processInstance.getProcessDefinitionId());
		System.out.println(processInstance.getProcessVariables().toString());
		assertNotNull(processInstance.getProcessVariables());
		Iterator<Entry<String, Object>> iterator = processInstance.getProcessVariables().entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry pairs = (Map.Entry)iterator.next();
			System.out.println(pairs.getKey() + " = " + pairs.getValue());
			iterator.remove(); // avoid concurrent modification exception
		}
		
		
		
	}
	
}