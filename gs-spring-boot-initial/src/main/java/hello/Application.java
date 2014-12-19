package hello;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.servlet.http.HttpServletRequest;

import org.activiti.engine.IdentityService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.identity.Group;
import org.activiti.engine.identity.User;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.MediaType;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.support.GenericHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.activiti.spring.integration.ActivitiInboundGateway;
import org.activiti.spring.integration.IntegrationActivityBehavior;
import org.h2.server.web.WebServlet;
import org.h2.tools.Server;
import org.h2.util.IOUtils;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.ServletRegistrationBean;

@Configuration
@EnableAutoConfiguration
@ComponentScan
/****************************************************************
 * Application.java
 * 
 * This is the main[] method for this Spring app
 *
 ****************************************************************/
public class Application {

	/* MVC */
	// Init mvc configuration
	@Configuration
	static class MvcConfiguration extends WebMvcConfigurerAdapter {
		@Override
		public void addViewControllers(ViewControllerRegistry registry) {
			registry.addViewController("/").setViewName("processLaunchPage");
		}
	}

	// Init security configuration
	@Configuration
	static class SecurityConfiguration extends WebSecurityConfigurerAdapter {
		protected void configure(HttpSecurity http) throws Exception {
			http.authorizeRequests().antMatchers("/approve")
					.hasAuthority("photoReviewers").antMatchers("/")
					.authenticated().and().csrf().disable().httpBasic();
		}
	}

	/* Initialize Activiti Spring Integration */

	// Initialize Activiti - IntegrationActivitiBehavior
	@Bean
	IntegrationActivityBehavior activitiDelegate(
			ActivitiInboundGateway activitiInboundGateway) {
		return new IntegrationActivityBehavior(activitiInboundGateway);
	}

	// Initialize Activiti - ActivitiInboundGateway
	@Bean
	ActivitiInboundGateway inboundGateway(ProcessEngine processEngine) {
		return new ActivitiInboundGateway(processEngine, "processed", "userId",
				"photo", "photos");
	}

	// Initialize Spring IntegrationFlow
	@Bean
	IntegrationFlow inboundProcess(
			ActivitiInboundGateway activitiInboundGateway,
			PhotoService photoService) {
		return IntegrationFlows.from(activitiInboundGateway)
				.handle(new GenericHandler<ActivityExecution>() {
					@Override
					public Object handle(ActivityExecution execution,
							Map<String, Object> headers) {

						Photo photo = (Photo) execution.getVariable("photo");
						Long photoId = photo.getId();
						System.out.println("integration: handling execution "
								+ headers.toString());
						System.out.println("integration: handling photo #"
								+ photoId);

						photoService.dogifyPhoto(photo);

						return MessageBuilder.withPayload(execution)
								.setHeader("processed", (Object) true)
								.copyHeaders(headers).build();
					}
				}).get();
	}

	// Initialize Spring Security
	@Bean
	CommandLineRunner init(IdentityService identityService) {
		return new CommandLineRunner() {
			@Override
			public void run(String... strings) throws Exception {

				// install groups & users
				Group approvers = group("photoReviewers");
				Group uploaders = group("photoUploaders");

				User joram = user("jbarrez", "Joram", "Barrez");
				identityService.createMembership(joram.getId(),
						approvers.getId());
				identityService.createMembership(joram.getId(),
						uploaders.getId());

				User josh = user("jlong", "Josh", "Long");
				identityService.createMembership(josh.getId(),
						uploaders.getId());

				User thong = user("thongh", "Thong", "Huynh");
				identityService.createMembership(thong.getId(),
						uploaders.getId());
				identityService.createMembership(thong.getId(),
						approvers.getId());
			}

			private User user(String userName, String f, String l) {
				User u = identityService.newUser(userName);
				u.setFirstName(f);
				u.setLastName(l);
				u.setPassword("password");
				identityService.saveUser(u);
				return u;
			}

			private Group group(String groupName) {
				Group group = identityService.newGroup(groupName);
				group.setName(groupName);
				group.setType("security-role");
				identityService.saveGroup(group);
				return group;
			}
		};

	}

	@Bean
	public ServletRegistrationBean h2servletRegistration() {
		ServletRegistrationBean registration = new ServletRegistrationBean(
				new WebServlet());
		registration.addUrlMappings("/console/*");
		return registration;
	}

	@Bean
	Server h2Server() {
		Server server = new Server();
		try {
			server.runTool("-tcp");
			server.runTool("-tcpAllowOthers");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return server;

	}

	/* Main */
	public static void main(String[] args) {
		ApplicationContext ctx = SpringApplication.run(Application.class, args);

		System.out.println("Let's inspect the beans provided by Spring Boot:");

		String[] beanNames = ctx.getBeanDefinitionNames();
		Arrays.sort(beanNames);
		for (String beanName : beanNames) {
			System.out.println(beanName);
		}

		ProcessEngine processEngine = ctx.getBean(ProcessEngine.class);

		System.out.println("*** Init the context");
		System.out.println("*** Process engine initiated: id "
				+ processEngine.getName());
	}

}

/****************************************************************
 * The data object repository
 * 
 * With Spring boot, when extending a JpaRepository, Spring boot will
 * automatically implement the repository for you
 *
 ****************************************************************/
interface PhotoRepository extends JpaRepository<Photo, Long> {
}

/****************************************************************
 * Application Entity
 * 
 *
 ****************************************************************/
@Entity
class Photo {

	@Id
	@GeneratedValue
	private Long id;

	private String userId;

	private boolean processed;

	public boolean isProcessed() {
		return processed;
	}

	public Long getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	// jpa
	Photo() {
	}

	public Photo(String userId) {
		this.userId = userId;
	}

	public Photo(String userId, boolean processed) {
		this.userId = userId;
		this.processed = processed;
	}
}

/****************************************************************
 * Service
 * 
 *
 ****************************************************************/
@Service
@Transactional
class PhotoService {

	@Autowired
	private TaskService taskService;

	@Value("file://#{ systemProperties['user.home'] }/Desktop/uploads")
	private Resource uploadDirectory;

	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private PhotoRepository photoRepository;

	@PostConstruct
	public void beforeService() throws Exception {
		File uploadDir = this.uploadDirectory.getFile();
		Assert.isTrue(uploadDir.exists() || uploadDir.mkdirs(), "the "
				+ uploadDir.getAbsolutePath() + " folder must exist!");
	}

	protected void writePhoto(Photo photo, InputStream inputStream) {
		try {
			try (InputStream input = inputStream;
					FileOutputStream output = new FileOutputStream(new File(
							this.uploadDirectory.getFile(), Long.toString(photo
									.getId())))) {
				IOUtils.copy(input, output);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public InputStream readPhoto(Photo photo) {
		try {
			return new FileInputStream(new File(this.uploadDirectory.getFile(),
					Long.toString(photo.getId())));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Photo createPhoto(String userId, InputStream bytesForImage) {
		Photo photo = this.photoRepository.save(new Photo((userId), false));
		writePhoto(photo, bytesForImage);
		return photo;
	}

	public ProcessInstance launchPhotoProcess(List<Photo> photos) {
		return runtimeService.startProcessInstanceByKey("photoProcess",
				Collections.singletonMap("photos", photos));
	}

	public ProcessInstance launchCCProcess(List<Photo> photos) {
		return runtimeService.startProcessInstanceByKey(
				"customerComplaintProcess",
				Collections.singletonMap("photos", photos));
	}

	public void dogifyPhoto(Photo photo) {

		// InputStream inputStream = this.photoManipulator.manipulate(() ->
		// this.readPhoto(photo)).getInputStream();
		// writePhoto(photo, inputStream);

		// do nothing

	}

	// @Autowired
	// private PhotoManipulator photoManipulator;
}

/****************************************************************
 * MVC Controller
 * 
 *
 ****************************************************************/

@Controller
class PhotoMvcController {

	@Autowired
	private PhotoRepository photoRepository;

	@Autowired
	private PhotoService photoService;

	@Autowired
	private TaskService taskService;

	@RequestMapping(method = RequestMethod.POST, value = "/upload")
	String upload(MultipartHttpServletRequest request, Principal principal) {

		System.out.println("uploading for " + principal.toString());
		Optional.ofNullable(request.getMultiFileMap())
				.ifPresent(m -> {

					// gather all the MFs in one collection
						List<MultipartFile> multipartFiles = new ArrayList<>();
						m.values().forEach((t) -> {
							multipartFiles.addAll(t);
						});

						// convert them all into `Photo`s
						List<Photo> photos = multipartFiles
								.stream()
								.map(f -> {
									try {
										return this.photoService.createPhoto(
												principal.getName(),
												f.getInputStream());
									} catch (IOException e) {
										throw new RuntimeException(e);
									}
								}).collect(Collectors.toList());

						System.out
								.println("started photo process w/ process instance ID: "
										+ this.photoService.launchPhotoProcess(
												photos).getId());

					});
		return "redirect:/";
	}

	@RequestMapping(value = "/image/{id}", method = RequestMethod.GET, produces = MediaType.IMAGE_JPEG_VALUE)
	@ResponseBody
	Resource image(@PathVariable Long id) {
		return new InputStreamResource(
				this.photoService.readPhoto(this.photoRepository.findOne(id)));
	}

	@RequestMapping(value = "/approve", method = RequestMethod.POST)
	String approveTask(@RequestParam String taskId,
			@RequestParam String approved) {
		boolean isApproved = !(approved == null || approved.trim().equals("")
				|| approved.toLowerCase().contains("f") || approved
				.contains("0"));
		this.taskService.complete(taskId,
				Collections.singletonMap("approved", isApproved));
		return "redirect:approve";
	}

	@RequestMapping(value = "/approve", method = RequestMethod.GET)
	String setupApprovalPage(Model model) {
		List<Task> outstandingTasks = taskService.createTaskQuery()
				.taskCandidateGroup("photoReviewers").list();
		if (0 < outstandingTasks.size()) {
			Task task = outstandingTasks.iterator().next();
			model.addAttribute("task", task);
			List<Photo> photos = (List<Photo>) taskService.getVariable(
					task.getId(), "photos");
			model.addAttribute("photos", photos);
		}
		return "approve";
	}

	@RequestMapping(method = RequestMethod.POST, value = "/launch")
	String launch(MultipartHttpServletRequest request, Principal principal) {

		System.out.println("Receiving complaint application by "
				+ principal.toString());

		Optional.ofNullable(request.getMultiFileMap())
				.ifPresent(m -> {

					// gather all the MFs in one collection
						List<MultipartFile> multipartFiles = new ArrayList<>();
						m.values().forEach((t) -> {
							multipartFiles.addAll(t);
						});

						// convert them all into `Photo`s
						List<Photo> photos = multipartFiles
								.stream()
								.map(f -> {
									try {
										return this.photoService.createPhoto(
												principal.getName(),
												f.getInputStream());
									} catch (IOException e) {
										throw new RuntimeException(e);
									}
								}).collect(Collectors.toList());

						System.out
								.println("started customer complaint process w/ process instance ID: "
										+ this.photoService.launchCCProcess(
												photos).getId());

					});

		return "redirect:/";
	}
}
