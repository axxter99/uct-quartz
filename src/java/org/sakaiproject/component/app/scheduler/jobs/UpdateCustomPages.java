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

import java.util.ArrayList;
import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateCustomPages implements Job {

	private SiteService siteService;
	private ServerConfigurationService serverConfigurationService;
	private SessionManager sessionManager;
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	public void setServerConfigurationService(
			ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}

	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}


	private ThreadLocalManager threadLocalManager;
	public void setThreadLocalManager(ThreadLocalManager threadLocalManager) {
		this.threadLocalManager = threadLocalManager;
	}



	public void execute(JobExecutionContext arg0) throws JobExecutionException {


		//set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId("admin");
		sakaiSession.setUserEid("admin");
		int first = 1;
		int increment = 1000;
		int last = increment;
		boolean doAnother = true;
		while (doAnother) {

			//List<Site> sites = siteService.getSites(SelectionType.ANY ,null, null, null, null, new PagingPosition(first, last));
			List<Site> sites = new ArrayList<Site>();
			try {
				Site thisone = siteService.getSite("1c6a1af5-4516-4f3b-b0c9-88055859df31");
				sites.add(thisone);
			} catch (IdUnusedException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			
			for (int i = 0; i < sites.size(); i++) {
				
				//SAK-17117 before we do this clear threadLocal
				//get the security advisor stack otherwise later calls will fail
				Object obj = threadLocalManager.get("SakaiSecurity.advisor.stack");
				threadLocalManager.clear();
				threadLocalManager.set("SakaiSecurity.advisor.stack", obj);
				sakaiSession = sessionManager.getCurrentSession();
				sakaiSession.setUserId("admin");
				sakaiSession.setUserEid("admin");
				
				boolean modified = false;
				Site site = sites.get(i);
				Site editSite = null;
				try {
					editSite = siteService.getSite(site.getId());
					List<SitePage> pages = editSite.getPages();
					for (int q = 0; q < pages.size(); q++) {
						SitePage page = pages.get(q);
						
						boolean custom = false;
						try {
							custom = page.getProperties().getBooleanProperty(SitePage.PAGE_CUSTOM_TITLE_PROP);
						} catch (EntityPropertyNotDefinedException e) {
							// we expect a lot of these
						} catch (EntityPropertyTypeException e) {
							log.warn(e.getMessage(), e);
						}
						if (!custom) {
							log.info("Page " + page.getTitle() + " has no custom flag");
							if (getTitleCustomLegacy(page, page.getTitle())) {
								//We need to add the property
								ResourceProperties rp = page.getProperties();
								rp.addProperty(SitePage.PAGE_CUSTOM_TITLE_PROP, "true");
								log.info("page " + page.getTitle() + " has been modified");
								modified = true;
							} else if (isMessagesPage(page)) {
								log.info("removing old messages page: " + page.getTitle());
								editSite.removePage(page);
								modified = true;
								
							} else {
								log.info("page " + page.getTitle() + " is not custom");
							}
						}

					}

				} catch (IdUnusedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				if (modified) {
					
					try {
						siteService.save(editSite);
					} catch (IdUnusedException e) {
						log.warn(e.getMessage(), e);
					} catch (PermissionException e) {
						log.warn(e.getMessage(), e);
					}
				}
			}
			if (sites.size() < increment) {
				doAnother = false;
			} else {
				first = last + 1;
				last = last + increment;
			}
		} //END WHILE

	}




	private boolean isMessagesPage(SitePage page) {
		
		// if	 more than one tool on this page, just return the default page title
		if ( page.getTools().size() != 1 )
		{
			return false;
		}
		
		String toolId = page.getTools().get(0).getToolId();
		if ("sakai.synoptic.messagecenter.HOLD".equals(toolId) && page.getTools().size() == 1) {
			return true;
		}
		
		return false;
	}

	/** Checks if this page's tool is a legacy iframe, news or linktool
	 ** that should assumed to have a custom page title 
	 ** (assumptions can be disabled with legacyPageTitleCustom = false).
	 *NOTE: this will not identify any other pages that where customized before
	 *this code was introduced see KNL-630 - DH
	 *
	 **/	 
	private boolean getTitleCustomLegacy(SitePage page, String m_title)
	{
		//
		boolean is_home = false;
		try {
			is_home = page.getProperties().getBooleanProperty(SitePage.IS_HOME_PAGE);
		} catch (EntityPropertyNotDefinedException e) {
			// we expect a lot of these
		} catch (EntityPropertyTypeException e) {
			log.warn(e.getMessage(), e);
		}
		
		if (is_home) {
			return false;
		}
		

		
		// if	 more than one tool on this page, just return the default page title
		if ( page.getTools().size() != 1 )
		{
			return false;
		}
		
		
		// Get the toolId of the first tool associated with this page
		String toolId = page.getTools().get(0).getToolId();
		String toolName = page.getTools().get(0).getTitle();

		if ( "sakai.iframe".equals(toolId) || "sakai.news".equals(toolId) || "sakai.rutgers.linktool".equals(toolId) || "sakai.synoptic.messagecenter.HOLD".equals(toolId) )
		{
			return false;
		}
		else if (m_title != null && !m_title.equals(toolName))
		{
			return true;
		}
		else
		{
			return false;
		}
	}


}
