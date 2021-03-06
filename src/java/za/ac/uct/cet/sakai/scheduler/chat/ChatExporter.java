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
package za.ac.uct.cet.sakai.scheduler.chat;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.chat2.model.ChatChannel;
import org.sakaiproject.chat2.model.ChatManager;
import org.sakaiproject.chat2.model.ChatMessage;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.useralias.logic.UserAliasLogic;
import org.sakaiproject.useralias.model.UserAliasItem;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatExporter implements Job {


	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}

	private ChatManager chatManager;	
	public void setChatManager(ChatManager chatManager) {
		this.chatManager = chatManager;
	}

	private SiteService  siteService;
	private UserDirectoryService userDirectoryService;

	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}

	private UserAliasLogic userAliasLogic;
	
	public void setUserAliasLogic(UserAliasLogic userAliasLogic) {
		this.userAliasLogic = userAliasLogic;
	}

	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId("admin");
		sakaiSession.setUserEid("admin");
		
		//FIXME should be via injection
		List<String> contextList = new ArrayList<String>();
		contextList.add("62db3011-e637-4047-be23-7c79cd858617");
		contextList.add("fe722e3b-7754-485d-90ac-2d1067e58a20");
		contextList.add("30dab44a-2359-4f69-93ea-bfd338ca6c4d");
		contextList.add("30dab44a-2359-4f69-93ea-bfd338ca6c4d");
		contextList.add("27dccf78-1022-4a23-a828-46ac0a8dd19a");
		contextList.add("a48c4b58-f46c-4c8e-86ed-0d2ee5467c8b");
		
		for (int q = 0; q < contextList.size(); q++) {

			String context = contextList.get(q);

			List<ChatChannel> channels = chatManager.getContextChannels(context, false);
			HSSFWorkbook myWorkBook = new HSSFWorkbook();
			//we need the Site title
			Site s = null;
			try {
				s = siteService.getSite(context);
			} catch (IdUnusedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				continue;
			}
			String path = "/data/sakai/otherdata/export/";
			String fileName = path + s.getTitle() + ".xls";
			for (int i = 0; i < channels.size(); i++) {
				ChatChannel channel = channels.get(i); 
				
				HSSFSheet mySheet = myWorkBook.createSheet(escapeSheetName(channel.getTitle()));
				int rowNum = 0;
				//set the headers
				HSSFRow headerR = mySheet.createRow(rowNum);
				rowNum++;
				
				headerR.createCell(0).setCellValue("Name");
				headerR.createCell(1).setCellValue("User Id");
				headerR.createCell(2).setCellValue("Date");
				headerR.createCell(3).setCellValue("Mesage");

				Set<ChatMessage> messages = channel.getMessages();
				Iterator<ChatMessage> iter= messages.iterator();
				
				while (iter.hasNext()) {
					ChatMessage message = iter.next();
					log.info(message.getBody());

					HSSFRow myRow = mySheet.createRow(rowNum);
					HSSFCell firstCell = myRow.createCell(0);
					//we need this user
					String displayName = message.getOwner();
					User u = null;
					try {
						u = userDirectoryService.getUser(message.getOwner());
						displayName = getAliasName(message.getOwner(), context);
						if (displayName == null) {
							
							displayName = u.getDisplayName();
						}
					} catch (UserNotDefinedException e) {
						log.warn(e.getMessage(), e);
						
					}

					firstCell.setCellValue(displayName);

					HSSFCell eid = myRow.createCell(1);
					eid.setCellValue(u.getEid());
					
							
							
					HSSFCell dateCell = myRow.createCell(2);
					dateCell.setCellValue(message.getMessageDate());

					HSSFCellStyle cs = myWorkBook.createCellStyle();
					HSSFDataFormat df = myWorkBook.createDataFormat();
					cs.setDataFormat(df.getFormat("m/d/yy h:mm"));
					dateCell.setCellStyle(cs);

					
					HSSFCell bodyCell = myRow.createCell(3);
					bodyCell.setCellValue(message.getBody());
					

					rowNum++;
				}
				mySheet.autoSizeColumn(3);
				try {
					FileOutputStream out = new FileOutputStream(fileName);
					myWorkBook.write(out);
					out.close();
				} catch (Exception e){ log.warn(e.getMessage(), e);} 
			}

		}
		log.info("Export Completed");
	}
	
	private String getAliasName(String owner, String context) {
		UserAliasItem  uai = userAliasLogic.getUserAliasItemByIdForContext(owner, "/site/" + context);
		if (uai != null) {
			return uai.getFirstName() + " " + uai.getLastName();
		}
		return null;
	}

	/**
	 * Escape a title for a worksheet name
	 * @param title
	 * @return
	 */
	private String escapeSheetName(String title) {
		title = title.replace("[", "");
		title = title.replace("]", "");		
		
		return title;
	}
}
