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

import java.text.NumberFormat;
import java.util.List;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.sakaiproject.chat2.model.ChatChannel;
import org.sakaiproject.chat2.model.ChatManager;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanOrphanedContent implements StatefulJob {

	private final String ADMIN = "admin";
	private SqlService sqlService;
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}

	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}

	private ContentHostingService contentHostingService;
	public void setContentHostingService(ContentHostingService contentHostingService) {
		this.contentHostingService = contentHostingService;
	}


	private SiteService siteService;	
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	private Boolean doCleanUp;
	public void setDoCleanUp(Boolean doCleanUp) {
		this.doCleanUp = doCleanUp;
	}
	

	private EmailService emailService;	
	public void setEmailService(EmailService emailService) {
		this.emailService = emailService;
	}

	private ChatManager chatManager;
	public void setChatManager(ChatManager chatManager) {
		this.chatManager = chatManager;
	}

	public void execute(JobExecutionContext context)
	throws JobExecutionException {

		//set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);

		/** 
		 * we may have orphaned sites
		 */
		String sql = "select site_id from SAKAI_SITE where site_id like '~%' and mid(site_id,2) not in (select user_id from SAKAI_USER)";
		List<String> res = sqlService.dbRead(sql);
		int orphanedSites = res.size();
		cleanUpOrphanedSites(res);

		sql = "select collection_id as cuid from CONTENT_COLLECTION where in_collection='/user/' and (replace(mid(collection_id,7),'/','') not in (SELECT user_id from SAKAI_USER_ID_MAP));";
		res = sqlService.dbRead(sql);
		long userBytes = getBytesInCollection(res);





		sql = "select collection_id from CONTENT_COLLECTION where in_collection='/group/' and replace(mid(collection_id,7),'/','') not in (select site_id from SAKAI_SITE) and length(collection_id)=length('/group/ffdd45d6-9e1d-4328-8082-646472c8b325/'); ";
		res = sqlService.dbRead(sql);
		long siteBytes = getBytesInCollection(res);
		
		
		/**
		 * Dropbox collection
		 */
		sql = "select collection_id from CONTENT_COLLECTION where in_collection='/group-user/' and replace(mid(collection_id,12),'/','') not in (select site_id from SAKAI_SITE) and length(collection_id)=length('/group-user/ffdd45d6-9e1d-4328-8082-646472c8b325/');";
		res = sqlService.dbRead(sql);
		long dbBytes = getBytesInCollection(res);

		/*
		sql = "select collection_id from CONTENT_COLLECTION where in_collection='/attachment/' and replace(mid(collection_id,13),'/','') not in (select site_id from SAKAI_SITE); ";
		res = sqlService.dbRead(sql);
		long attachBytes = getBytesInCollection(res);
		 */
		
		
		
		//orphaned chat channels
		sql = "select CHAT2_CHANNEL.channel_id from CHAT2_CHANNEL where context not in (select site_id from SAKAI_SITE);";
		res = sqlService.dbRead(sql);
		for (int i = 0; i < res.size(); i++) {
			ChatChannel channel = chatManager.getChatChannel(res.get(i));
			try {
				chatManager.deleteChannel(channel);
			} catch (PermissionException e) {
				log.warn("PermissionException", e);;
			}
		}
		
		
		
		
		
		
		
		
		
		
		
		
		log.info("Orphaned content in user collections: " + formatSize(userBytes * 1024));
		//log.info("Orphaned content in attachment collections: " + attachBytes);
		log.info("Orphaned content in site collections: " + formatSize(siteBytes * 1024));
		log.info("Orphaned content in dropBox collections: " + formatSize(dbBytes * 1024));
		log.info("Orphaned my workspace sites: " + orphanedSites);
		
		String body = "Orphaned content in user collections: " + formatSize(userBytes * 1024) + "\n";
		body += "Orphaned content in site collections: " + formatSize(siteBytes * 1024) + "\n";
		body += "Orphaned content in dropBox collections: " + formatSize(dbBytes * 1024) + "\n";
		body += "Orphaned my workspace sites: " + orphanedSites;
		
		
		
		
		
		emailService.send("help@vula.uct.ac.za", "help-team@vula.uct.ac.za", "Orphaned data cleaned", body, null, null, null);
	}


	private void cleanUpOrphanedSites(List<String> res) {
		for (int i = 0; i < res.size(); i++) {
			String site_id = res.get(i);
			try {
				Site site = siteService.getSite(site_id);
				siteService.removeSite(site);
			} catch (IdUnusedException e) {
				log.warn(e.getMessage(), e);
			} catch (PermissionException e) {
				log.warn(e.getMessage(), e);
			}
		}

	}

	private long getBytesInCollection(List<String> collectionList) {
		long userBytes = 0;
		log.info("got a result of: " + collectionList.size());
		for (int i = 0 ; i < collectionList.size(); i ++) {
			String r = (String)collectionList.get(i);
			log.debug("got resource: " + r);

			try {
				ContentCollection  collection = contentHostingService.getCollection(r);
				long thisOne = collection.getBodySizeK();
				log.info("Collection " + r + " has " + thisOne  + "K in the collection");
				userBytes = userBytes + thisOne;

				//delete the resource
				if (doCleanUp) {
					if (r != null) {
						contentHostingService.removeCollection(r);
					}
				}
			} catch (IdUnusedException e) {
				log.warn(e.getMessage(), e);
			} catch (TypeException e) {
				log.warn(e.getMessage(), e);
			} catch (PermissionException e) {
				log.warn(e.getMessage(), e);
			} catch (InUseException e) {
				log.warn(e.getMessage(), e);
			} catch (ServerOverloadException e) {
				log.warn(e.getMessage(), e);
			} catch (Exception e) {
				log.warn(e.getMessage(), e);;
			}

		} //For each collection
		return userBytes;
	}


	/**
	 * Utility method to get a nice short filesize string.
	 * @param size_long The size to be displayed (bytes).
	 * @return A short human readable filesize.
	 */
	private static String formatSize(long size_long) {
		// This method needs to be moved somewhere more sensible.
		String size = "";
		NumberFormat formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(1);
		if (size_long > 700000000L)
		{

			size = formatter.format(1.0 * size_long / (1024L * 1024L * 1024L)) + "G";

		}
		else if (size_long > 700000L)
		{
			
			size = formatter.format(1.0 * size_long / (1024L * 1024L)) + "Mb";

		}
		else if (size_long > 700L)
		{		
			size = formatter.format(1.0 * size_long / 1024L) + "kb";
		}
		else 
		{
			size = formatter.format(size_long) + "b";
		}
		return size;
	}

}
