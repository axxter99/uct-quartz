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
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

import com.novell.ldap.LDAPAttribute;
import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPEntry;
import com.novell.ldap.LDAPException;
import com.novell.ldap.LDAPJSSESecureSocketFactory;
import com.novell.ldap.LDAPSearchConstraints;
import com.novell.ldap.LDAPSearchResults;
import com.novell.ldap.LDAPSocketFactory;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class UCTCheckAccounts implements Job {

	
	
	
	private static final String TYPE_STUDENT = "student";
	private static final String TYPE_STAFF = "staff";
	private static final String TYPE_THIRDPARTY = "thirdparty";
	
	private static final String STATUS_INACTIVE = "Inactive";
	private static final String STATUS_INACTIVE_STAFF = "inactiveStaff";
	private static final String STATUS_INACTIVE_THIRDPARTY = "inactiveStaff";
	
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}


	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private static final String ADMIN = "admin";
	
	private String ldapHost = ""; //address of ldap server
	private int ldapPort = 389; //port to connect to ldap server on
	private int operationTimeout = 5000; //default timeout for operations (in ms)
	
	public void setLdapHost(String ldapHost) {
		this.ldapHost = ldapHost;
	}


	public void setLdapPort(int ldapPort) {
		this.ldapPort = ldapPort;
	}
	
	private String basePath;
	/**
	 * @param basePath The basePath to set.
	 */
	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}

	private CourseManagementAdministration courseManagementAdministration;
	private CourseManagementService courseManagementService;
	
	
	
	public void setCourseManagementAdministration(CourseManagementAdministration courseAdmin) {
		this.courseManagementAdministration = courseAdmin;
	}


	public void setCourseManagementService(CourseManagementService cmService) {
		this.courseManagementService = cmService;
	}


	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
	    
	    //set up the secure connection
	    LDAPSocketFactory ssf = new LDAPJSSESecureSocketFactory();
		LDAPConnection.setSocketFactory(ssf);
		
		int first = 1;
		int increment = 1000;
		int last = increment;
		boolean doAnother = true;
		while (doAnother) {
			
		
			List<User> users = userDirectoryService.getUsers(first, last);
			for (int i= 0; i < users.size(); i++ ){
				User u = (User)users.get(i);
				if (doThisUser(u)) {
					if (!userExists(u.getEid())) {
						log.warn("user: " + u.getEid() + " does not exist in auth tree" );
						try {
							//if this is a student remove from current courses in this year
							if ("student".equalsIgnoreCase(u.getType()))
								removeFromCourses(u);

							UserEdit ue = userDirectoryService.editUser(u.getId());
							ue.setType(getInactiveType(u.getType()));
							userDirectoryService.commitEdit(ue);

						} catch (UserNotDefinedException e) {
							log.warn(e.getMessage(), e);
						} catch (UserPermissionException e) {
							log.warn(e.getMessage(), e);
						} catch (UserLockedException e) {
							log.warn(e.getMessage(), e);
						} catch (UserAlreadyDefinedException e) {
							log.warn(e.getMessage(), e);
						}

					} else {
						log.debug("user: " + u.getEid() + " is in ldap");
					}


				}



			}
			if (users.size() < increment) {
				doAnother = false;
			} else {
				first = last + 1;
				last = last + increment;
			}
		}
	}
	
	
	private String getInactiveType(String type) {
		String ret= STATUS_INACTIVE;
		if (TYPE_STAFF.equals(type)) {
			ret = STATUS_INACTIVE_STAFF;
		} else if (TYPE_THIRDPARTY.equals(type)) {
			ret = STATUS_INACTIVE_THIRDPARTY;
		}
		
		return ret;
	}


	private void removeFromCourses(User u) {
		
		String userEid = u.getEid();
		Set<EnrollmentSet> enroled = courseManagementService.findCurrentlyEnrolledEnrollmentSets(userEid);
		Iterator<EnrollmentSet> coursesIt = enroled.iterator();
		log.debug("got list of enrolement set with " + enroled.size());
		 while (coursesIt.hasNext()) {
			EnrollmentSet eSet = (EnrollmentSet)coursesIt.next();
			log.info("removing user from " + eSet.getEid());
			String courseEid =  eSet.getEid();
			courseManagementAdministration.removeCourseOfferingMembership(userEid, courseEid);
			courseManagementAdministration.removeSectionMembership(userEid, courseEid);
			courseManagementAdministration.removeEnrollment(userEid, courseEid);
		 }
	}


	private boolean doThisUser(User u) {
		if (TYPE_STAFF.equalsIgnoreCase(u.getType()) || TYPE_THIRDPARTY.equalsIgnoreCase(u.getType())) {
			return true;
		} else if (TYPE_STUDENT.equalsIgnoreCase(u.getType())) {
			//skip the valid testing accounts
			if (u.getEid().indexOf("user") == 0 || u.getEid().indexOf("tii") == 0 || u.getEid().indexOf("test") == 0 || u.getEid().indexOf("student") == 0) 
				return false;
				
			return true;
		}
		
		return false;
		
	}
	
	
	private boolean userExists(String id) {
		
		LDAPConnection conn = new LDAPConnection();
		String sFilter = "cn=" + id;

		String[] attrList = new String[] { "dn", "loginDisabled" };
		try {
			conn.connect( ldapHost, ldapPort );
			//this will fail if user does not exist	
			LDAPEntry userEntry = getEntryFromDirectory(sFilter,attrList,conn);
			if (userEntry == null) {
				conn.disconnect();
				return false;
			}
			
			LDAPAttribute atr = userEntry.getAttribute("loginDisabled");
			if (atr != null) {
			 String disabled = atr.getStringValue();
			 log.debug("LoginDisabled:" + disabled);
			 if ("TRUE".equals(disabled)) {
				 log.warn(id + ": Acccount is disabled in auth tree");
				 conn.disconnect();
				 return false;
			 }
			}
			conn.disconnect();
		
		}
		catch (Exception e)
		{
			
			return false;	
		}		
		return true;
	}
	
	// search the directory to get an entry
	private LDAPEntry getEntryFromDirectory(String searchFilter, String[] attribs, LDAPConnection conn)
	throws LDAPException
	{
		LDAPEntry nextEntry = null;
		LDAPSearchConstraints cons = new LDAPSearchConstraints();
		cons.setDereference(LDAPSearchConstraints.DEREF_NEVER);		
		cons.setTimeLimit(operationTimeout);

		LDAPSearchResults searchResults =
			conn.search(basePath,
					LDAPConnection.SCOPE_SUB,
					searchFilter,
					attribs,
					false,
					cons);

		if (searchResults.hasMore()){
			nextEntry = searchResults.next();            
		}

		return nextEntry;
	}



}
