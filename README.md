activiti-spring-boot
====================

This is a Spring boot application project created to embed Activiti engine into a Spring app. 
This project is inspired by the Spring Activiti Integration webinar from Josh Long
The key reason why I'm doing this is that I want to figure out how you do BPM with Activiti. BPM is a completely different way of building software, unlike 
any other type of typical software development with Java. A BPM project needs to be executed fast and should be done in about 3 months, more or less. 

After looking at Activiti and what it's offering at the moment as a BPM solution, I can see a couple of limitation:
 - The ability to quickly create custom screens/forms/UI for user task (currently, the user task form is rendered automatically and it's horribly useless)
 - The ability to create proper process variables (currently, activiti allows you to create 'form properties' but it is no where near a proper data model for a BPM application)

So this Spring application sort of make over for these 2 limitations, with Josh Long webinar, I can see that I can:
 - Create custom user task UI for activiti's user task with Spring MVC 
 - Create a more proper data model for my BPM application with Spring JPA 
 - And of course, these things are connected with Activiti process engine 

However, this is only good for a project where you try to embed a workflow into existing web applications. The ideal fix that I think should make Activiti much better is that
 - The availability of a user task form designer which is directly integrated to the process created by activiti-explorer or eclipse designer plugin
 - The availability of a proper process variable modeling mechanism which is also built into activiti-explorer or eclipse designer plugin
 



