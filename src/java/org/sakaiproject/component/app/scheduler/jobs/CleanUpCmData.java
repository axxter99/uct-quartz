/**********************************************************************************
 *
 * Copyright (c) 2019 University of Cape Town
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.component.app.scheduler.jobs;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.coursemanagement.api.CanonicalCourse;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.CourseOffering;
import org.sakaiproject.coursemanagement.api.CourseSet;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanUpCmData implements Job {
	
	
	private static final String ADMIN = "admin";

	private CourseManagementService courseManagementService;
	private CourseManagementAdministration courseAdmin;
	
	public void setCourseManagementService(CourseManagementService cs) {
		courseManagementService = cs;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	public void setCourseManagementAdministration(CourseManagementAdministration cs) {
		courseAdmin = cs;
	}
	
	
	private String term;
	public void setTerm(String t) {
		term = t;
	}
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
	    log.info("about to clean up cm date in term: " + term);
		Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
	    
	    //AcademicSession thisTerm = courseManagementService.getAcademicSession(term);
	    
	    //first get course sets
	    List<CourseSet> depts = courseManagementService.findCourseSets("Department");
	    
	    for (int i = 0 ; i < depts.size(); i++) {
	    	CourseSet dept = (CourseSet) depts.get(i);
	    	log.info("got set: " + dept.getEid());
	    	Set<CanonicalCourse> canonCourses = courseManagementService.getCanonicalCourses(dept.getEid());
	    	Iterator<CanonicalCourse> it = canonCourses.iterator();
	    	while (it.hasNext()) {
	    		CanonicalCourse course = (CanonicalCourse) it.next();
	    		String eid = course.getEid();
	    		if (eid.lastIndexOf("SUP") == 8 || eid.lastIndexOf("EWA") == 8) {
	    			log.warn("Found course to delete: " + eid);
	    			String fullEid = eid + "," + term;
	    			String fullEid2 = eid + ",2010";
	    			//we only need to remove at this level
	    			//we need to remove the course offering from its course set
	    			Set<String> courseSets = course.getCourseSetEids();
	    			Iterator<String> ita = courseSets.iterator();
	    			while (ita.hasNext()) {
	    				String set = (String)ita.next();
	    				courseAdmin.removeCanonicalCourseFromCourseSet(set, eid);
	    			}
	    			
	    			//course offering
	    			if (courseManagementService.isCourseOfferingDefined(fullEid)) {
	    				CourseOffering co = courseManagementService.getCourseOffering(fullEid);
	    				Set<String> sets = co.getCourseSetEids();
	    				Iterator<String> itb = sets.iterator();
	    				while (itb.hasNext()) {
	    					String set = (String)itb.next();
	    					courseAdmin.removeCourseOfferingFromCourseSet(set, fullEid);
	    				}
	    				Set<Section> s = courseManagementService.getChildSections(fullEid);
	    				Iterator<Section> it2 = s.iterator();
	    				while (it2.hasNext()) {
	    					Section section = it2.next();
	    					courseAdmin.removeSection(section.getEid());
	    				}
	    				
	    				courseAdmin.removeCourseOffering(fullEid);
	    				
	    			}
	    			
	    			if (courseManagementService.isCourseOfferingDefined(fullEid2)) {
	    				CourseOffering co = courseManagementService.getCourseOffering(fullEid2);
	    				Set<String> sets = co.getCourseSetEids();
	    				Iterator<String> itb = sets.iterator();
	    				while (itb.hasNext()) {
	    					String set = (String)itb.next();
	    					courseAdmin.removeCourseOfferingFromCourseSet(set, fullEid2);
	    				}
	    				Set<Section> s = courseManagementService.getChildSections(fullEid2);
	    				Iterator<Section> it2 = s.iterator();
	    				while (it2.hasNext()) {
	    					Section section = it2.next();
	    					courseAdmin.removeSection(section.getEid());
	    				}
	    				
	    				courseAdmin.removeCourseOffering(fullEid2);
	    				
	    			}
	    			
	    				
	    			//canon course
	    			courseAdmin.removeCanonicalCourse(eid);
	    		}
	    	}
	    }
	    
	    
	}

}
