package edu.stevens.cs562;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.StringTokenizer;

//Grouping variable class -- object for each grouping variable
//contains such that for each GV and aggregate lists
public class groupingVar {
	
	private String name;
	private String predicate;
	private LinkedHashMap<String, Aggregates> aggregateList = new LinkedHashMap<String, Aggregates>();
	
	public groupingVar(String name){
		this.name = name;
	}
	
	public groupingVar(String name, String predicate){
		this.name = name;
		this.predicate = predicate;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public HashMap<String, Aggregates> getAggregateList() {
		return aggregateList;
	}
	public String getPredicate() {
		return predicate;
	}
	public void setPredicate(String predicate) {
		//remove group name and append mf here
		StringTokenizer st = new StringTokenizer(predicate);
		StringBuilder sb = new StringBuilder();
		String s;

		while(st.hasMoreTokens()){
			String temp = st.nextToken();
			//check if token has any aggregate less attributes
			if(temp.contains("_")){
				s = temp.substring(0,temp.indexOf('_'));  //this will give us either group name or aggregate fn over entire

				if(!Aggregates.isAggregate(temp)){
					//only for non-aggregates
					
					//check if this is part of mf-structure then append prefix i.e. grouping attributes
					boolean ifFound = false;
					
					for(String ga:QueryProcessor.getGroupAttr()){
						if(ga.equals(temp)){
							temp = QueryProcessor.prefix + "." + temp; 
							ifFound = true;
							break;
						} else if (ga.equals(temp.substring(temp.indexOf('_')+1, temp.length()))){
							temp = QueryProcessor.prefix + "." + temp.substring(temp.indexOf('_')+1, temp.length());
							ifFound = true;
							break;
						}
						
					}
					
					if(!ifFound){
						//just remove the first part so only column name is there
						temp = temp.substring(temp.indexOf('_')+1, temp.length());
					}
									
				} else{
					//for aggregates
					temp = QueryProcessor.prefix + "." + temp;
				}
			}
			sb.append(temp).append(" ");  //adding space again as it was causing issues later on
		}
		
		this.predicate = sb.toString();
	}
	
	public void addAggregate(Aggregates function){
		Aggregates sum, count; 
		//in case of AVG add count and sum as well
		if(function.getFunction().equalsIgnoreCase("avg")){
			if(function.isEntire()){
				sum = new Aggregates("sum",function.getColumn());
				count = new Aggregates("count",function.getColumn());

			} else {
				sum = new Aggregates(function.getGv(),"sum",function.getColumn());
				count = new Aggregates(function.getGv(),"count",function.getColumn());
			}
			
			aggregateList.put(sum.getName(), sum);
			aggregateList.put(count.getName(), count);
		}
		
		aggregateList.put(function.getName(),function); //ensure avg is after sum and count
	}
}
