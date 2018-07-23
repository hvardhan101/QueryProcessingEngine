//Auto-Created by Foreign Keys
//This is the Query Program

package edu.stevens.cs562;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class emfQuery {
private static ArrayList<mfStructure> mftable = new ArrayList<mfStructure>();
private static Connection dbconn;
private static String create = "";
private static String remove = "";
private static String Query = "select * from sales where year = 1997";
private static String from = "sales";
private static ResultSet rs = null;
private static Statement statement = null;

public static void main(String[] args) { 
//connect to database
dbconn = QueryProcessor.connectToDb();
//Prepare the mftable
fetchData();
//remove any db views create
removeView();
//Display results
display();
}

public static void fetchData() {
try {
statement = dbconn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);  //create view
if(create!="") {
statement.executeUpdate(create);
}
rs = statement.executeQuery(Query);
} catch (SQLException e) {
e.printStackTrace();
}
//Fill mf_table ..looping through loop map

try {

//0th scan to populate all rows
while(rs.next()) {
boolean ifFound = false;
//get all grouping attributes
String cust = rs.getString("cust");
int day = rs.getInt("day");
int month = rs.getInt("month");
String prod = rs.getString("prod");
int quant = rs.getInt("quant");
String state = rs.getString("state");
int year = rs.getInt("year");

//loop through mf_table
for(mfStructure mf:mftable) {
//create row if not found
if(mf.cust.equals(cust) && mf.month == month) { //All grouping attributes
ifFound = true;
break;}
}
if(!ifFound) {
mfStructure mnew = new mfStructure();
//All grouping attributes
mnew.cust = cust;
mnew.month = month;

mftable.add(mnew);
}
}

rs.beforeFirst();
while(rs.next()) {
//get all grouping attributes
String cust = rs.getString("cust");
int day = rs.getInt("day");
int month = rs.getInt("month");
String prod = rs.getString("prod");
int quant = rs.getInt("quant");
String state = rs.getString("state");
int year = rs.getInt("year");

//loop through mf_table
for(mfStructure mf:mftable) {
//create row if not found
if(mf.cust.equals(cust) && mf.month == month) { //All grouping attributes
//Calculating aggregates for Group entire
mf.sum_quant = mf.sum_quant + quant;
mf.count_quant = mf.count_quant + 1;
mf.avg_quant = mf.sum_quant / mf.count_quant;
}
if(mf.cust .equals( cust ) && mf.month < month ) { //Grouping clause for x
//Calculating aggregates for Group x
mf.x_sum_quant = mf.x_sum_quant + quant;
mf.x_count_quant = mf.x_count_quant + 1;
mf.x_avg_quant = mf.x_sum_quant / mf.x_count_quant;
}
if(mf.cust .equals( cust ) && mf.month > month ) { //Grouping clause for y
//Calculating aggregates for Group y
mf.y_sum_quant = mf.y_sum_quant + quant;
mf.y_count_quant = mf.y_count_quant + 1;
mf.y_avg_quant = mf.y_sum_quant / mf.y_count_quant;
}
}
}

} catch (SQLException e) {
e.printStackTrace();
}
}

public static void removeView(){
try {
if(create!=""){
statement = dbconn.createStatement();  //create view
statement.execute(remove);
}
statement.close();
} catch (SQLException e) {
e.printStackTrace();
}
}

public static void display(){
//header for result set
System.out.println("------------------------------------------------------------------------------------------");

System.out.printf("%-15s%15s%15s%15s%15s\n","cust","month","avg_quant","x_avg_quant","y_avg_quant");
System.out.println("------------------------------------------------------------------------------------------");

for(mfStructure mf:mftable) {
if(true ) {
System.out.printf("%-15s%15d%15f%15f%15f\n",mf.cust,mf.month,mf.avg_quant,mf.x_avg_quant,mf.y_avg_quant);
}
}
}
}
