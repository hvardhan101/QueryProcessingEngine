package edu.stevens.cs562;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.WordUtils;

public class QueryProcessor {

	public static String nl = System.getProperty("line.separator");


	//Variables to make DB connection
	private static String url, user, password, table;
	private static Connection dbconn;

	//property file keys
	public static String keyUser   = "User";	
	public static String keyPass   = "Password";
	public static String keyURL    = "URL";
	public static String keyTable  = "Table";

	//property file keys for Query
	public static String keySelect   = "(.*)select(.*)";	
	public static String keyFrom     = "(.*)from(.*)";
	public static String keyWhere    = "(.*)where(.*)";
	public static String keyGroup    = "(.*)group.by(.*)";
	public static String keyNumber   = "(.*)number(.*)";	
	//public static String keyGroups   = "*groups*";	
	public static String keySuchThat = "(.*)such.that(.*)";
	public static String keyHaving   = "(.*)having(.*)";

	//SQL to java
	public static String sqlAnd = "(( )*)(a|A)(n|N)(d|D)(( )*)";
	public static String sqlOr  = "(( )*)(o|O)(r|R)(( )*)";
	public static String sqlEq  = "(.*)=(.*)";
	public static String sqlNe  = "(.*)<>(.*)";
	public static String sqlCom = "(.*)'*'(.*)";

	public enum operators {SELECT, FROM, WHERE, NUMBER, GROUP, SUCHTHAT, HAVING, JOIN, SELECTION, SQLAND, SQLOR, SQLEQ, SQLNE, SQLCOMMA, NONE}


	//Query parameters
	private static int 		noOfGV;
	private static String 	from;
	private static String 	having;
	private static String   whereClause;
	private static String 	selAttrS;
	private static String[] selAttr;
	private static String 	groupAttrS;
	private static String[] groupAttr;
	private static String 	groupVarS;
	private static String[] groupVars;
	private static String   suchThatS;
	private static String[] suchThats;

	private static String   createView = ""; //SQL query for crete view
	private static String   dropView = "";

	private static TreeMap<String, groupingVar> gvList = new TreeMap<String, groupingVar>();

	public static String tempView  = "tempviewCS562";
	public static String join 	   = "(.*)join(.*)";
	public static String selection = "(.*)select(.*)";
	public static String token	   = "[;:]";

	public static int tableSize    = 0;
	public static Path currdir     = Paths.get("");

	public static String cwd = currdir.toAbsolutePath().toString();
	public static String pkg =  QueryProcessor.class.getPackage().getName();//QueryProcessor.class.getName(); 
	//files

	public static String credfile    = "resources/credentials.txt";
	public static String mfStructure = cwd + "/src/edu/stevens/cs562/mfStructure.java";  //cwd.substring(0, cwd.lastIndexOf('\\'))
	//public static String mfStructure = "/src/edu/stevens/cs562/mfStructure.java";

	public static String emfQuery    = cwd + "/src/edu/stevens/cs562/emfQuery.java";

	private static BufferedWriter writer;

	private static HashMap<String, ArrayList<String>> loops = new HashMap<String, ArrayList<String>>(); 
	private static int gMax = 0;
	public static String prefix = "mf";  //name of mf structure   i.e looper for mf table

	//Column data types
	public static TreeMap<String, String> colDetails;

	private static boolean isEntireScan = false;

	private static String totalPredicate;

	public static String header, format;

	public static void main(String[] args) throws IOException{
		//connect to database
		dbconn = connectToDb();
		//read the EMF query file or console
		getQuery();
		//get the table details
		colDetails = getTableDetails(dbconn, from);
		having = convertToJava(having, gvList);  //moved to here as it requires information schema details

		//get the size
		tableSize = getTableSize(from);
		//prepare the grouping variable class
		gvList = prepareGV(groupVars,suchThats,selAttr,having);
		//prepare looping table
		prepareLoops();

		//Rewrite the having clause
		having = addIterator(having);

		//Prepare the MF_STRUCTURE
		prepareStructure(mfStructure);
		//Prepare the query program
		prepareQueryProg(emfQuery);
		System.out.println("****************************");
		System.out.println("Program files generated at:");
		System.out.println("****************************");
		System.out.println(mfStructure);
		System.out.println(emfQuery);

	}

	//return connection to DB if credential file is present use it otherwise prompt user
	public static Connection connectToDb(){
		if(dbconn!= null)
			return dbconn;
		else{
			Properties cred = new Properties();

			//read the credentials
			try {
				FileInputStream f = new FileInputStream(credfile);
				cred.load(f);
				f.close();
				Enumeration<Object> prop = cred.keys();
				while(prop.hasMoreElements()){
					String key = (String)prop.nextElement();
					if(key.equals(keyURL))
						url = cred.getProperty(key);
					else if(key.equals(keyUser))
						user = cred.getProperty(key);
					else if(key.equals(keyPass))
						password = cred.getProperty(key);
					else if(key.equals(keyTable))
						table = cred.getProperty(key);
				} 
			}catch (IOException e) {
				saveCredentials(cred);
			} finally {
				//connect
				try {
					Class.forName("org.postgresql.Driver");
					System.out.println("Success loading Driver!");
					try {
						dbconn = DriverManager.getConnection(url, user, password);
						System.out.println("Connected");
					}catch (SQLException ex) {
						//prompt user for new credentials
						System.out.println("Credentials failed! Want to try new credentials[Y/y]");
						Scanner scanner = new Scanner(System.in);
						if (scanner.hasNext() && scanner.nextLine().equalsIgnoreCase("y")) {
							saveCredentials(cred);
						} else {
							System.exit(0);
						}
					}
					return dbconn;
				} catch (Exception ex) {
					System.out.println("Failed loading Driver!");
					ex.printStackTrace();
				}
			}

		}
		return dbconn;
	}

	public static void saveCredentials(Properties cred){
		// Prompt user for credentials
		Scanner scanner = new Scanner(System.in);

		System.out.println("Enter URL for Postgres server: ");
		url =  scanner.nextLine();
		cred.setProperty(keyURL, url);
		System.out.println("Enter User name: ");
		user = scanner.nextLine();
		cred.setProperty(keyUser, user);
		System.out.println("Enter password: ");
		password = scanner.nextLine();
		cred.setProperty(keyPass, password);
		System.out.println("Enter DB Table Name: ");
		table = scanner.nextLine();
		cred.setProperty(keyTable, table);
		try {

			File file = new File(credfile);
			if(!file.exists()) {
				file.createNewFile();
			}
			cred.store(new FileOutputStream(credfile), "credentials");
			System.out.println("Credentials updated! ");
		} catch (IOException e1) {
			e1.printStackTrace();
		}			
		scanner.close();
	}

	public static void getQuery(){
		String str;
		System.out.print("Enter file path+name or 'C'|'c' for console: ");
		Scanner scanner = new Scanner(System.in);
		String choice = scanner.nextLine();
		do{
			if(choice.equalsIgnoreCase("C")){
				//Console
				System.out.println("Enter all projected attibutes separated by ',' ");
				selAttrS = scanner.nextLine();
				selAttr = parseSelect(selAttrS);

				System.out.println("Enter the 'from'(name of view/table) ");
				parseFrom(scanner.nextLine());

				System.out.println("Input Where Clause: ");
				str = scanner.nextLine();
				if(str!=null && str.trim().length() > 0)
					whereClause = str;

				System.out.println("Input No. of Grouping Variables: ");		
				noOfGV = Integer.parseInt(scanner.nextLine());

				System.out.println("Input Grouping Attributes separated by ','");
				groupAttrS = scanner.nextLine();
				groupAttr = parseGroupBy(groupAttrS);

				System.out.println("Input Grouping variables");
				groupVarS = scanner.nextLine();
				groupVars = parseGroupNames(groupVarS);

				System.out.println("Input Grouping predicates separated by ','");
				suchThatS = scanner.nextLine();
				suchThats = parseSuchThats(suchThatS);

				System.out.println("Input Having clause");
				parseHaving(scanner.nextLine());

			} else {
				//input file
				Properties input = new Properties();

				//read the credentials
				try {		

					FileInputStream f = new FileInputStream(choice);
					input.load(f);
					f.close();
					Enumeration<Object> prop = input.keys();
					while(prop.hasMoreElements()){
						String key = (String)prop.nextElement();

						switch(matchKey(key)){
						case SELECT:
							selAttrS = input.getProperty(key);
							selAttr = parseSelect(selAttrS);
							break;

						case FROM:
							parseFrom(input.getProperty(key));
							break;

						case WHERE:
							str = input.getProperty(key);
							if(str!=null && str.trim().length() > 0)
								whereClause = str;
							break;


						case NUMBER:
							noOfGV = Integer.parseInt(input.getProperty(key));
							break;

						case GROUP:
							str = input.getProperty(key);
							String[] group = str.split(token);
							//there will be two strings
							groupAttrS = group[0];
							groupAttr = parseGroupBy(groupAttrS);
							groupVarS = group[1];
							groupVars = parseGroupNames(groupVarS);
							break;

						case SUCHTHAT:
							suchThatS = input.getProperty(key);
							suchThats = parseSuchThats(suchThatS);
							break;

						case HAVING:
							parseHaving(input.getProperty(key));
							break;

						default:
							break;						
						}


					} 
				}catch (IOException e) {
					//e.printStackTrace();
					System.out.println("File couldn't be read. Please check and enter again. Press ['q'|'Q'] to quit");
					choice = scanner.nextLine();

				}
			}
		}while("q".equalsIgnoreCase(choice));


	}

	public static String[] getGroupAttr(){
		return groupAttr;
	}

	public static operators matchKey(String key){
		Pattern p;
		Matcher m;

		p = Pattern.compile(keySelect, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.SELECT;
		} 

		p = Pattern.compile(keyFrom, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.FROM;
		} 

		p = Pattern.compile(keyWhere, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.WHERE;
		} 

		p = Pattern.compile(keyGroup, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.GROUP;
		} 

		p = Pattern.compile(keyNumber, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.NUMBER;
		} 

		p = Pattern.compile(keySuchThat, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.SUCHTHAT;
		} 

		p = Pattern.compile(keyHaving, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.HAVING;
		} 

		p = Pattern.compile(join, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.JOIN;
		} 

		p = Pattern.compile(selection, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.SELECTION;
		} 

		p = Pattern.compile(sqlAnd, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.SQLAND;
		} 

		p = Pattern.compile(sqlOr, Pattern.CASE_INSENSITIVE);
		m = p.matcher(key);
		if(m.matches()){
			return operators.SQLOR;
		} 

		p = Pattern.compile(sqlEq);
		m = p.matcher(key);
		if(m.matches()){
			return operators.SQLEQ;
		} 

		p = Pattern.compile(sqlNe);
		m = p.matcher(key);
		if(m.matches()){
			return operators.SQLNE;
		} 

		p = Pattern.compile(sqlCom);
		m = p.matcher(key);
		if(m.matches()){
			return operators.SQLCOMMA;
		} 

		return operators.NONE;

	}

	public static String[] parseSelect(String select){
		select = removeSpace(select);
		return select.split(",");		
	}

	public static void parseFrom(String select){
		//Check if simple relation name or sub-query/ join
		String[] split = select.split("\\s");

		if(split.length > 1){
			//must be a query
			switch (matchKey(select)){
			case SELECTION:
				createView = "create view "+ tempView +"as " + select + ";";
				break;
			case JOIN:
				createView = "create view "+ tempView +"as select * from " + select + ";";
				break;
			}		
			from = tempView;
			dropView   = "drop view " + tempView  + " if exits;";

		} else 
			from = select;

	}

	public static String[] parseGroupBy(String str){
		str = removeSpace(str);
		return str.split(",");
	}

	//get the group names first
	public static String[] parseGroupNames(String str){
		str = removeSpace(str);
		return str.split(",");
	}

	public static String[] parseSuchThats(String str){
		str = removeSpace(str);
		return str.split(",");
	}

	public static String removeSpace(String s) {
		s = s.replaceAll("\\s+,",",").replaceAll(",\\s+", ",").trim();
		return s;
	}

	public static void parseHaving(String str){
		str.trim();
		if((str==null)||(str.length()==0)){
			having = "true";
		} else {
			having = str;
			//having = convertToJava(str, gvList);
		}
	}

	public static TreeMap<String,String> getTableDetails(Connection conn, String table) {
		TreeMap<String, String> map = new TreeMap<String, String>();
		String colName, dataType, type;
		String sql = "select column_name, data_type from information_schema.columns where table_name = '" + table + "'";
		Statement statement = null;

		try {
			statement = conn.createStatement();
			ResultSet rs = statement.executeQuery(sql);

			while (rs.next()) 
			{
				colName  = rs.getString("column_name");
				dataType = rs.getString("data_type");

				switch(dataType) {
				case "text":
				case "char":
				case "name":
				case "character varying":
				case "character":
					type = "String";
					break;
				case "integer":
				case "bigint":
				case "smallint":
					type = "int";
					break;
				case "boolean":
					type = "boolean";
					break;
				default:
					type = "String";
					break;

				}
				map.put(colName, type);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return map;

	}

	//give us tablesize of a relation otherwise returns 500.. run it after temp view is created
	public static int getTableSize(String table) {
		int size = 0;
		String sql = "select seq_tup_read from pg_stat_user_tables where relname = '" + table + "'";
		Statement statement = null;

		try {
			statement = dbconn.createStatement();
			ResultSet rs = statement.executeQuery(sql);

			while (rs.next()) {
				size  = rs.getInt("seq_tup_read");
			}

		} catch(SQLException e) {
			size = 500;
			return size;
		} 		
		return size;
	}

	public static TreeMap<String, groupingVar> prepareGV(String[] gvs, String[] sts, String[] sa, String hav) {

		TreeMap<String, groupingVar> list = new TreeMap<String, groupingVar>();
		//"prep" "entire"
		groupingVar group = new groupingVar("prep");
		list.put("prep", group);
		totalPredicate = getTotalPredicate(sa);
		group.setPredicate(totalPredicate);


		int i = 0;
		for(String s:gvs) {
			group = new groupingVar(s);
			list.put(s, group);
			//find the such that clause for it .. Predicates
			//this method also adds any aggregate functions to the list
			group.setPredicate(convertToJava(sts[i],list));
			i++;				
		}

		//find aggregate functions in projection
		for(String s:sa){
			convertToJava(s,list); //would take care of most operations and add aggregate functions
		}

		//find aggregates in having
		convertToJava(hav,list);	

		return list;
	}

	public static String convertToJava(String s, TreeMap<String, groupingVar> gList) {
		String temp, prev = null, type;
		StringBuilder sb = new StringBuilder();
		//change SQL notation... AND, OR, <>, = to java compatibles
		StringTokenizer st = new StringTokenizer(s);
		type = " ";
		while (st.hasMoreTokens()) {
			temp = st.nextToken();
			switch(matchKey(temp)){
			case SQLAND:
				//replace temp with equivalent expression
				temp = " && ";
				break;
			case SQLOR:
				temp = " || ";
				break;
			case SQLEQ:
				//check previous token 
				type = findAtt(prev);
				if((type!=null)&&(type.equals("String"))){
					temp = ".equals(";
				} else {	
					temp = " == ";
				}
				break;
			case SQLNE:
				temp = " != ";
				break;
			case SQLCOMMA:
				temp = "\"" + temp.substring(1, temp.length()-1) + "\"";
				break;
			default:
				//check for aggregates and add to the list
				if (Aggregates.isAggregate(temp)) {
					Aggregates agg = new Aggregates(temp);	
					groupingVar gv = addAggToGv(agg, gList);						
				} 

				temp = " " + temp + " ";

				break;					
			}
			
			if(temp.equals(" != ")){
				sb.append(".intern()");
			}
			
			sb.append(temp);
			if((prev!=null)&&(prev.equals(".equals("))){
				sb.append(")"); //closing brackets
			}

			if((prev!=null)&&(prev.equals(" != "))){
				type = findAtt(temp);
				if((type!=null)&&(type.equals("String"))){
					sb.append(".intern()");
				} 
			}
			prev = temp;

		}
		return sb.toString();			
	}

	public static String addIterator(String s){
		String temp;
		StringBuilder sb = new StringBuilder();
		StringTokenizer st = new StringTokenizer(s);

		while(st.hasMoreTokens()){
			temp = st.nextToken();
			if(Aggregates.isAggregate(temp)){
				//append aggregates with prefix
				sb.append(prefix).append(".");
			}
			sb.append(temp).append(" ");
		}	

		return sb.toString();
	}

	public static void setIsEntireScan(){
		isEntireScan = true;
	}

	public static String findAtt(String s){
		//first check if this is an aggregate
		if(Aggregates.isAggregate(s)){
			Aggregates a = new Aggregates(s);
			return a.getType();
		} else {
		
			String split[] = s.trim().split("_");
			System.out.println(split[split.length -1]);
			for(Entry<String,String> e:colDetails.entrySet()){
				System.out.println(e.getValue()+":"+e.getKey());
			}
			
			if(colDetails.containsKey(split[split.length - 1])){
				return colDetails.get(split[split.length - 1]);
			} else
				return null;
		}
	}

	public static groupingVar addAggToGv(Aggregates agg, TreeMap<String, groupingVar> gList){
		System.out.println(agg.getName());
		String s = agg.getGv();
		groupingVar gv = null;
		if(s!=null){
			gv = gList.get(s);
		}
		if(gv!=null){
			gv.addAggregate(agg);
		} else{
			//create the grouping var
			gv = new groupingVar(s);
			gList.put(gv.getName(), gv);
			gv.addAggregate(agg);
		}
		return gv;
	}

	public static void prepareLoops() {
		Set<String> dep;
		//parse and see the such that clauses and prepare the iterations
		int i = 0, j =1;
		//0th loop
		loops.put("0", new ArrayList<String>(Arrays.asList("prep")));
		//check if entire table group is there
		if(isEntireScan){
			loops.put("1", new ArrayList<String>(Arrays.asList("entire")));
		}

		for(String s:suchThats) {
			dep = QueryProcessor.checkDependency(s,groupVars[i]);
			int max = 1;
			if(!dep.isEmpty()){
				//dependency .. check max loop value for all dependencies
				for(String st:dep){ //check st in gvList
					Set<Entry<String, ArrayList<String>>> EntrySet = loops.entrySet();
					for(Entry<String,ArrayList<String>> e:EntrySet){
						//iterate list for a loop number
						for(String val:e.getValue()){
							if(val.toUpperCase().equals(st.toUpperCase())){
								int temp = Integer.parseInt(e.getKey());
								if(temp>max){max = temp;}
							}
						}
					}
				}
				ArrayList<String> temp = loops.get(Integer.toString(max+1));
				if(temp==null){
					temp = new ArrayList<String>();
				}
				temp.add(groupVars[i]);
				loops.put(Integer.toString(max+1), temp);
				if(gMax < max+1){ gMax = max + 1;}

			} else{
				//no dependency calculate in 1st loop
				ArrayList<String> temp = loops.get("1");
				if(temp==null){
					temp = new ArrayList<String>();
				}
				temp.add(groupVars[i]);
				loops.put("1", temp);
			}
			i++;
		}
	}

	public static void prepareStructure(String mfFile){

		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		StringBuilder sb3 = new StringBuilder();
		StringBuilder sb4 = new StringBuilder();

		boolean ifFirst = true;
		//open file
		//iterate arrays for projected attributes and push them in file
		try {
			writer = new BufferedWriter(new FileWriter(mfFile));

			writeToFile("//Auto-Created by Foreign Keys", true, writer);
			writeToFile("//This is MF_STRUCTURE", true, writer);
			writeToFile("package " + pkg + ";", true, writer);
			writeToFile(" ", true, writer);
			writeToFile("public class mfStructure {", true, writer);
			writeToFile(" ", true, writer);
			//add attributes, only columns

			for(String s:selAttr) {
				String type, comma = ",";

				if(ifFirst){
					sb.append("System.out.printf(\"");
					sb3.append("System.out.printf(\"");
					ifFirst = false;
				} 

				if(!Aggregates.isAggregate(s)) {
					type = colDetails.get(s);
					writeToFile("public "+colDetails.get(s)+" "+s+";", true, writer);
				} else{
					//check aggregate type
					Aggregates a = new Aggregates(s);
					System.out.println(s);
					type = a.getType();
				}
				//prepare the header string to be displayed later
//				sb.append("%15s");
				sb2.append(comma).append("\"").append(s).append("\"");
				sb4.append(comma).append(prefix).append(".").append(s);
				if("String".equalsIgnoreCase(type)){
					sb.append("%-15s");
					sb3.append("%-15s");
				} else if("double".equalsIgnoreCase(type)){
					sb.append("%15s");
					sb3.append("%15f");
				} else{
					sb.append("%15s");
					sb3.append("%15d");
				}
			}
			sb2.append(");");
			header = sb.append("\\n\"").append(sb2.toString()).toString();

			sb4.append(");");
			format = sb3.append("\\n\"").append(sb4.toString()).toString();


			//add aggregates
			for(Entry<String, groupingVar> e:gvList.entrySet()){
				Set<Entry<String, Aggregates>> iter = e.getValue().getAggregateList().entrySet();
				for(Entry<String, Aggregates> i:iter){
					Aggregates a = i.getValue();
					writeToFile("public "+a.getType()+" "+ a.getName()+";", true, writer);
				}
			}
			//end of class
			writeToFile("}", true, writer);

		} catch (IOException e) {
			e.printStackTrace();
		} finally { 		
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void prepareQueryProg(String qFile) {

		StringBuilder sb  = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		String tempS = null, tempS2 = null, tempS3 = null;

		try {				
			writer = new BufferedWriter(new FileWriter(qFile));

			writeToFile("//Auto-Created by Foreign Keys" + nl 
					+  "//This is the Query Program" + nl + nl 
					+  "package " + pkg + ";", true, writer);

			//imports
			writeToFile("import java.sql.Connection;" + nl
					+   "import java.sql.ResultSet;" + nl
					+   "import java.sql.SQLException;" + nl
					+   "import java.sql.Statement;" + nl
					+	"import java.util.ArrayList;" + nl, true, writer);

			//Start of class
			writeToFile("public class emfQuery {", true, writer);

			//Variable definitions
			//check whereclause
			sb.append("private static String Query = \"select * from ").append(from);
			if((whereClause!=null)&&(whereClause!="")){
				sb.append(" where ").append(whereClause);
			} 
			tempS = sb.append("\";").append(nl).toString();
			sb.setLength(0);
			
			writeToFile("private static ArrayList<mfStructure> mftable = new ArrayList<mfStructure>();" + nl
					+ 	"private static Connection dbconn;" + nl
					+	"private static String create = \"" + createView + "\";" + nl
					+	"private static String remove = \"" + dropView + "\";" + nl
					+   tempS
				//	+	"private static String Query = \"select * from " + from + " where " + whereClause +"\";" + nl
					+ 	"private static String from = \"" + from + "\";" + nl
					+	"private static ResultSet rs = null;" + nl
					+	"private static Statement statement = null;" + nl
					, true ,writer);

			//Start method main 
			writeToFile("public static void main(String[] args) { " + nl
					+	"//connect to database" + nl
					+	"dbconn = QueryProcessor.connectToDb();" + nl
					+	"//Prepare the mftable" + nl
					+	"fetchData();" + nl							
					+	"//remove any db views create" + nl
					+	"removeView();" + nl
					+	"//Display results" + nl
					+	"display();" + nl
					+	"}" + nl

					, true, writer);
			//End method main 


			//Start Method fetchData
			writeToFile("public static void fetchData() {" + nl
					+	"try {" + nl
					+	"statement = dbconn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);  //create view" + nl
					+	"if(create!=\"\") {" + nl
					+	"statement.executeUpdate(create);" + nl
					+	"}" + nl
					+	"rs = statement.executeQuery(Query);" + nl
					+	"} catch (SQLException e) {" + nl
					+	"e.printStackTrace();" + nl
					+	"}" + nl
					+   "//Fill mf_table ..looping through loop map" + nl + nl
					+   "try {" + nl
					, true, writer);


			//make a temp string where we read the db cursor to variable with same names
			sb.setLength(0);
			for(Map.Entry<String,String> entry :colDetails.entrySet()){
				sb.append(entry.getValue()).append(" ").append(entry.getKey()).append(" = ").append("rs.get").append(WordUtils.capitalize(entry.getValue()))
				.append("(\"").append(entry.getKey()).append("\");").append(nl);
			}
			tempS = sb.toString();
			sb.setLength(0);

			//Assign all grouping attributes value from cursor
			for(String s:groupAttr) {					
				sb2.append("mnew.").append(s).append(" = ").append(s).append(";").append(nl);
			}
			tempS3 = sb2.toString();
			sb2.setLength(0);


			boolean isZero = false;
			boolean ifFirst = true;

			Set<Entry<String, ArrayList<String>>> EntrySet = loops.entrySet();
			for(Entry<String,ArrayList<String>> e:EntrySet){

				if(e.getKey().equals("0")){
					isZero = true;
				} else{
					isZero = false;
				}

				//prepare 0th scan with the first iteration
				if(isZero){
					sb.append("//0th scan to populate all rows" + nl);
				}

				if(!ifFirst){
					sb.append("rs.beforeFirst();").append(nl);
				}
				ifFirst = false;

				sb.append("while(rs.next()) {" + nl);
				if(isZero){
					sb.append("boolean ifFound = false;" + nl);
				}
				sb.append("//get all grouping attributes" + nl).append(tempS + nl).append("//loop through mf_table" + nl)
				.append("for(mfStructure ").append(prefix).append(":mftable) {" + nl).append("//create row if not found" + nl);

				//loop through grouping variable with same loop number
				for(String s:e.getValue()) {
					sb.append("if(");
					if(s.equalsIgnoreCase("entire")||s.equalsIgnoreCase("prep")){
						sb.append(totalPredicate).append(") { //All grouping attributes" + nl);
					} else {
						//find predicates for current grouping variable
						sb.append(gvList.get(s).getPredicate()).append(") { //Grouping clause for ").append(s).append(nl);
					}

					//calculate aggregates
					if(!s.equalsIgnoreCase("prep")){
						sb.append("//Calculating aggregates for Group ").append(s).append(nl);
						for(Aggregates a: gvList.get(s).getAggregateList().values()){
							sb.append(Aggregates.expandAgg(prefix,a)).append(nl);
						}
					} else { //0th scan code "prep"
						sb.append("ifFound = true;").append(nl).append("break;");
					}

					sb.append("}").append(nl);

				}

				sb.append("}").append(nl);

				if(isZero){
					//code to add lines in 0th scan
					sb.append("if(!ifFound) {").append(nl).append("mfStructure mnew = new mfStructure();").append(nl)
					.append("//All grouping attributes").append(nl).append(tempS3).append(nl).append("mftable.add(mnew);")
					.append(nl).append("}").append(nl);
				}

				sb.append("}").append(nl);  //end of while loop

				writeToFile(sb.toString(), true, writer);
				sb.setLength(0);						
			}


			//end of Try
			writeToFile("} catch (SQLException e) {" + nl
					+	"e.printStackTrace();" + nl
					+	"}" + nl
					+   "}" + nl    //end of fetchData method


					, true, writer);

			//Start of Method RemoveView
			sb.setLength(0);
			sb.append("public static void removeView(){").append(nl).append("try {").append(nl).append("if(create!=\"\"){").append(nl)
			.append("statement = dbconn.createStatement();  //create view").append(nl).append("statement.execute(remove);")
			.append(nl).append("}").append(nl).append("statement.close();").append(nl).append("} catch (SQLException e) {").append(nl)
			.append("e.printStackTrace();").append(nl).append("}").append(nl).append("}").append(nl);
			//End of method removeview
			writeToFile(sb.toString(), true, writer);

			sb.setLength(0);

			//Start of method display
			//Format the display string
			int i = 0;
			sb2.append("System.out.println(\"");
			while(i <= selAttr.length){
				sb2.append("---------------");
				i++;
			}
			String buffer = sb2.append("\");").append(nl).toString();
			
			sb.append("public static void display(){").append(nl).append("//header for result set").append(nl).append(buffer).append(nl)
			.append(header).append(nl).append(buffer).append(nl)
			//for loop on mf table + having clause
			.append("for(mfStructure mf:mftable) {").append(nl).append("if(").append(having).append(") {").append(nl)

			//printing code for actual data lines
			.append(format).append(nl).append("}").append(nl).append("}").append(nl)

			//End of method display
			.append("}").append(nl)
			//end of class
			.append("}");

			writeToFile(sb.toString(), true, writer);

		} catch (IOException e) {
			e.printStackTrace();
		} finally { 		
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private static String getTotalPredicate(String[] ga){
		StringBuilder sb = new StringBuilder();
		String and = "";
		for(String s:ga) {					
			if(!Aggregates.isAggregate(s)){
				sb.append(and).append(prefix).append(".").append(s);
				if(findAtt(s).equals("String")){
					sb.append(".equals(").append(s).append(")");
				} else {
					sb.append(" == ").append(s);
				}
				and = " && ";
			}
		}
		return sb.toString();
	}

	private static void writeToFile(String s, boolean flag, BufferedWriter writer) throws IOException {
		if(flag) {
			writer.write(s);
			writer.newLine();
		} else {
			writer.write(s);
		}
	}

	public static Set<String> checkDependency(String s, String group){
		Set<String> deps = new HashSet<String>();
		String dep = null, temp = null;
		StringTokenizer st = new StringTokenizer(s);
		while(st.hasMoreTokens()){
			dep = st.nextToken();
			if(Aggregates.isAggregate(dep)){
				// get dependency group
				//check if dependency on entire
				String split[] = dep.split("_");
				if(split.length == 3){
					deps.add(split[0]);
				} else {
					deps.add("entire");
				}								
			}
		}

		return deps;
	}

}