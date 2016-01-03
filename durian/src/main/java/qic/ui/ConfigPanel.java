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
package qic.ui;

import static qic.util.Config.CONFIG_PROPERTIES_FILENAME;
import static qic.util.SwingUtil.showError;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.IOException;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.apache.commons.io.FileUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qic.Main;
import qic.util.Config;
import qic.util.Util;

/**
 * @author thirdy
 *
 */
public class ConfigPanel extends RTextScrollPane {
	private static final long serialVersionUID = 1L;
	private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

	RSyntaxTextArea textArea = new RSyntaxTextArea();

	public ConfigPanel() {
	    textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
		setViewportView(textArea);
		loadConfigToTextArea();
		
		textArea.addFocusListener(new FocusListener() {
			
			@Override
			public void focusLost(FocusEvent e) {
				saveAndReloadConfig();
				Main.reloadLookAndFeel();
			}
			
			@Override
			public void focusGained(FocusEvent e) {
				loadConfigToTextArea();
			}

		});
	}

	void saveAndReloadConfig() {
		try {
			// directly calling getText() from TextArea is causing bad values, probably swing quirks
			Document document = textArea.getDocument();
			String content = document.getText(0, document.getLength());
			Util.overwriteFile(CONFIG_PROPERTIES_FILENAME, content);
			Config.loadConfig();
		} catch (BadLocationException | IOException e) {
			logger.error("Error while saving to " + CONFIG_PROPERTIES_FILENAME);
			showError(e);
		}
	}

	private void loadConfigToTextArea() {
		try {
			String str = FileUtils.readFileToString(new File(CONFIG_PROPERTIES_FILENAME));
			textArea.setText(str);
			textArea.setCaretPosition(0);
		} catch (IOException e) {
			logger.error("Error while reading " + CONFIG_PROPERTIES_FILENAME);
			showError(e);
		}
	}

	public void tabSelected() {
		textArea.requestFocusInWindow();
	}
}
