/*
 * Copyright (C) 2015 thirdy
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package qic;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static qic.Command.Status.ERROR;
import static qic.util.Config.GUILD_LIST_FILENAME;

import java.awt.Window;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qic.BlackmarketLanguage.ParseResult;
import qic.Command.Status;
import qic.SearchPageScraper.SearchResultItem;
import qic.ui.QicFrame;
import qic.util.CommandLine;
import qic.util.Config;
import qic.util.FmJS;
import qic.util.SessProp;
import qic.util.Util;

/**
 * TODO, REFACTOR!!!
 * 
 * @author thirdy
 *
 */
public class Main {
	
	private final static Logger logger = LoggerFactory.getLogger(Main.class.getName());
	
	BackendClient backendClient = new BackendClient();
	SessProp sessProp = new SessProp();
	Long searchDuration = null; 
	List<String> invalidSearchTerms = null; 
	
	public static void main(String[] args) throws Exception {
		try {
			reloadConfig();
			new Main(args);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "Error occured: " + e.getMessage());
			throw e;
		}
    }

	public static void reloadConfig() throws Exception, FileNotFoundException {
		Config.loadConfig();
	}
	
	public static void reloadLookAndFeel() {
		if (Config.getBooleanProperty(Config.LOOK_AND_FEEL_DECORATED, false)) {
			JFrame.setDefaultLookAndFeelDecorated(true); 
			JDialog.setDefaultLookAndFeelDecorated(true);
			System.setProperty("sun.awt.noerasebackground", "true");
		}
		try {
			String lookAndFeel = Config.getPropety(Config.LOOK_AND_FEEL, "com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
			if (lookAndFeel.startsWith("Substance")) {
				lookAndFeel = "org.pushingpixels.substance.api.skin." + lookAndFeel;
			}
			UIManager.setLookAndFeel(lookAndFeel);
		} catch (Exception e) {
		    try {
		        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		    } catch (Exception ex) {
		    	throw new RuntimeException(ex);
		    }
		}
		
		for (Window window : Window.getWindows()) {
			SwingUtilities.updateComponentTreeUI(window);
		}
	}

	public Main(String[] args) throws Exception {
		CommandLine cmd = new CommandLine(args);
		boolean guiEnabled = cmd.hasFlag("-gui");
		guiEnabled = guiEnabled || cmd.getNumberOfArguments() == 0;

		logger.info("guiEnabled: " + guiEnabled);
		
		if (guiEnabled) {
			showGui(cmd.getArgument(0));
		} else {
			if (cmd.getNumberOfArguments() == 0) {
				throw new IllegalArgumentException("First arguement needed, and should be the query. e.g. 'search chest 100life 6s5L'. "
						+ "Enclosed in double quoutes if needed.");
			}
			String query = cmd.getArgument(0);
			logger.info("Query: " + query);
			
			Command command = processLine(query);
			String json = command.toJson();
			writeToFile(json);
		}
	}

	private void showGui(String query) {
		reloadLookAndFeel();
	    SwingUtilities.invokeLater(() -> new QicFrame(Main.this, query));
	}

	public void writeToFile(String contents) throws IOException {
		Util.overwriteFile("results.json", contents);
	}

	public Command processLine(String line) throws Exception {
		Command command = new Command(line);
		searchDuration = null;
		invalidSearchTerms = new LinkedList<>();
		try {
			if (line.startsWith("sort")&& !sessProp.getLocation().isEmpty()) {
				command.itemResults = runSearch(line, true);
				command.searchDuration = searchDuration;
			} else if (line.startsWith("s ")) {
				String terms = substringAfter(line, "s ").trim();
				if (!terms.isEmpty()) {
					command.itemResults = runSearch(terms, false);
					command.searchDuration = searchDuration;
				}
			}
			command.league = sessProp.getLeague();
			command.invalidSearchTerms = invalidSearchTerms;
			command.status = Status.SUCCESS;
		} catch (Exception e) {
			e.printStackTrace();
			command.status = ERROR;
			command.errorMessage = e.getMessage();
			command.errorStackTrace = ExceptionUtils.getStackTrace(e);
			throw e;
		}
		setGuildInfo(command.itemResults);
		FmJS fmJS = new FmJS(command.itemResults);
		fmJS.process();
		return command;
	}

	private void setGuildInfo(List<SearchResultItem> itemResults) throws IOException {
		String nameList = FileUtils.readFileToString(new File(GUILD_LIST_FILENAME));
		if (!nameList.isEmpty()) {
			List<String> names = Arrays.asList(StringUtils.split(nameList));
			itemResults.stream().forEach(item -> {
				boolean guildmate = names.stream().anyMatch(name -> StringUtils.equalsIgnoreCase(name, item.seller()));
				item.guildItem(guildmate);
				item.guildDiscount(Config.getPropety(Config.GUILD_DISCOUNT_STRING, Config.GUILD_DISCOUNT_STRING_DEFAULT));
			});
		}
	}

	private List<SearchResultItem> runSearch(String terms, boolean sortOnly) throws Exception {
		String html = downloadHtml(terms, sortOnly);
		if (html == null) {
			return Collections.emptyList();
		}
		SearchPageScraper scraper = new SearchPageScraper(html);
		List<SearchResultItem> items = scraper.parse();
		logger.info("items found: " + items.size());
		return items;
	}

	public String downloadHtml(String terms, boolean sortOnly) throws Exception {
		long start = System.currentTimeMillis();
		
		String regex = "([^\\s]*=\".*?\")";
		List<String> customHttpKeyVals = Util.regexMatches(regex, terms, 1);
		String customHttpKeyVal = customHttpKeyVals.stream()
				.map(s -> StringUtils.remove(s, '"'))
				.collect(Collectors.joining("&")); 
		String query = terms.replaceAll(regex, " ");
		
		BlackmarketLanguage language = new BlackmarketLanguage();
		ParseResult sortParseResult = language.parseSortToken(query);
		String sort = sortParseResult.result;
		sort = sort == null ? "price_in_chaos" : sort;
		invalidSearchTerms.addAll(sortParseResult.invalidSearchTerms);
		
		if (!sortOnly) {
			logger.info("Query: " + query);
			ParseResult queryParseResult = language.parse(query);
			String payload = queryParseResult.result;
			invalidSearchTerms.addAll(queryParseResult.invalidSearchTerms);
			if (invalidSearchTerms.isEmpty()) {
				payload = asList(payload, customHttpKeyVal, "capquality=x").stream().filter(StringUtils::isNotBlank).collect(joining("&"));
				logger.info("Unencoded payload: " + payload);
				payload = asList(payload.split("&")).stream().map(Util::encodeQueryParm).collect(joining("&"));
				String location  = submitSearchForm(payload);
				String league = language.parseLeagueToken(query);
				sessProp.setLocation(location);
				sessProp.setLeague(league);
				sessProp.saveToFile();
			}
		}

		String searchPage = null;
		if (invalidSearchTerms.isEmpty()) {
			logger.info("sort: " + sort);
			searchPage = ajaxSort(sort);
		} else {
			logger.info("invalidSearchTerms: " + invalidSearchTerms.toString());
		}
		
		long end = System.currentTimeMillis();
		long duration = end - start;
		logger.info("Took " + duration + " ms");
		searchDuration = Long.valueOf(duration);
		return searchPage;
	}

	private String ajaxSort(String sort) throws Exception {
		String searchPage = "";
		sort = URLEncoder.encode(sort, "UTF-8");
		sort = "sort=" + sort + "&bare=true";
		searchPage = backendClient.postXMLHttpRequest(sessProp.getLocation(), sort);
		return searchPage;
	}

	private String submitSearchForm(String payload) throws Exception {
		String url = "http://poe.trade/search";
		String location = backendClient.post(url, payload);
		return location;
	}

}
