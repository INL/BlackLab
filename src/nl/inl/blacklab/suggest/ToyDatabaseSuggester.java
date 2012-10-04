/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.suggest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A quick test for a Suggester using a database.
 */
public class ToyDatabaseSuggester extends Suggester {
	private Connection conn;

	public ToyDatabaseSuggester(Connection connection) {
		conn = connection;
	}

	/*
	 *
	 * CREATE TEST DATABASE:
	 *
	 * create database testsuggest; use testsuggest create table suggest (word varchar(255), variant
	 * varchar(255)); insert into suggest('leuk', 'leuker');
	 */

	@Override
	public void addSuggestions(String original, Suggestions sugg) {
		try {
			PreparedStatement stmt = conn
					.prepareStatement("SELECT variant FROM suggest WHERE word = ?");
			try {
				stmt.setString(1, original);
				ResultSet rs = stmt.executeQuery();
				while (rs.next()) {
					sugg.addSuggestion("toy", rs.getString(1));
				}
			} finally {
				stmt.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws ClassNotFoundException, SQLException {
		String url = "jdbc:mysql://localhost:3306/testsuggest?useUnicode=true&characterEncoding=utf8&autoReconnect=true";
		// Register the JDBC driver for MySQL.
		Class.forName("com.mysql.jdbc.Driver");
		Connection conn = DriverManager.getConnection(url, "jan", "");
		try {
			Suggester dbs = new ToyDatabaseSuggester(conn);
			String[] w = new String[] { "fijn", "leuk" };
			for (String word : w) {
				System.out.println(word + ": " + dbs.suggest(word));
			}
		} finally {
			conn.close();
		}

	}

}
