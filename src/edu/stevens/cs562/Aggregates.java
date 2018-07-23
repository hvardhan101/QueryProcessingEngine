package edu.stevens.cs562;

//splits into function and column
public class Aggregates {
	
	public enum aggregates {SUM, COUNT, AVG, MAX, MIN, FIRST, LAST, NOT}
	
	private String function;
	private String column;
	private String gv;
	private boolean isEntire;
	
	public String getFunction(){return function;}
	public String getColumn(){return column;}
	public String getGv(){return gv;}
	public boolean isEntire(){return isEntire;}
	
	public Aggregates(String gv, String func, String col){
		this.gv = gv;
		this.function = func;
		this.column = col;
		this.isEntire = false;
	}
	
	public String getType(){
		switch(matchAgg(function)){
		case SUM:
			return "int";
		case AVG:
			return "double";
		case COUNT:
			return "int";
		case FIRST:
		case LAST:
		case MAX:
		case MIN:
			return  QueryProcessor.colDetails.get(column);
		case NOT:
			return "String";
		default:
			return "String";			
		}
	}
	
	public Aggregates(String func, String col){
		this.function = func;
		this.column = col;
		this.isEntire = true;
		gv       = "entire";
	}
	
	public String getName(){
		if((gv!=null)&&!("entire".equals(gv))){
			return this.gv + "_" + this.function + "_" + this.column;
		} else {
			return this.function + "_" + this.column;
		}
	}
	
	public static aggregates matchAgg(String str){

		if (str.toUpperCase().equals(aggregates.AVG.name())){
			return aggregates.AVG;
		}
		else if (str.toUpperCase().equals(aggregates.SUM.name())){
			return aggregates.SUM;
		}
		else if (str.toUpperCase().equals(aggregates.COUNT.name())){
			return aggregates.COUNT;
		}
		else if (str.toUpperCase().equals(aggregates.MAX.name())){
			return aggregates.MAX;
		}
		else if (str.toUpperCase().equals(aggregates.MIN.name())){
			return aggregates.MIN;
		}
		else if (str.toUpperCase().equals(aggregates.FIRST.name())){
			return aggregates.FIRST;
		}
		else if (str.toUpperCase().equals(aggregates.LAST.name())){
			return aggregates.LAST;
		}
		else
			return aggregates.NOT;
	}
	
	public static boolean isAggregate(String str){
		String f[] = str.split("_");
		if(f.length < 2){
			return false;
		} else {
			if(matchAgg(f[f.length -2])!=aggregates.NOT) {
				//check if table aggregate
				if(f.length == 2){
					QueryProcessor.setIsEntireScan();
				}
				return true;
			} else
				return false;
		}
	}
	
	public static String expandAgg(String prefix, Aggregates funct){
		String fn = funct.getName();
		String ex;		
	
		switch(matchAgg(funct.getFunction())){
				
			case AVG:
				ex = prefix + "." + fn + " = " + prefix + "." + fn.replace(funct.getFunction()+"_", "sum_") + " / " 
							+ prefix + "." + fn.replace(funct.getFunction()+"_", "count_") + ";";
				break;
			
			case SUM:
				ex = prefix + "." + fn + " = " + prefix + "." + fn + " + " + funct.getColumn() + ";";
				break;
			
			case COUNT:
				ex = prefix + "." + fn + " = " + prefix + "." + fn + " + 1;";
				break;
							
			case MAX:
				if(funct.getType().equals("String")){
					//String comparison
					ex = "if(" + funct.getColumn() + ".compareTo(" + prefix + "." + fn + ") > 0) {"  
							+ prefix + "." + fn + " = " + funct.getColumn() + "; }";
				} else {
					//int/double comparison
					ex = "if(" + funct.getColumn() + " > " + prefix + "." + fn + ") { "
							+ prefix + "." + fn + " = " + funct.getColumn() + "; }";							
				}
				break;
				
			case MIN:
				if(funct.getType().equals("String")){
					//String comparison
					ex = "if(" + funct.getColumn() + ".compareTo(" + prefix + "." + fn + ") < 0) {"  
							+ prefix + "." + fn + " = " + funct.getColumn() + "; }";
				} else {
					//int/double comparison
					ex = "if(" + funct.getColumn() + " < " + prefix + "." + fn + ") { "
							+ prefix + "." + fn + " = " + funct.getColumn() + "; }";							
				}
				break;
			case FIRST:
				if(funct.getType().equals("String")){
					//String comparison
					ex = "if(" + prefix + "." + fn + "!=\"\") {"  
							+ prefix + "." + fn + " = " + funct.getColumn() + "; }";
				} else {
					//int/double comparison
					ex = "if(" + prefix + "." + fn + "!=0) {"  
							+ prefix + "." + fn + " = " + funct.getColumn() + "; }";						
				}			
				break;
				
			case LAST:
				ex = prefix + "." + fn + " = " + funct.getColumn() + ";";

				break;
			case NOT:
			default:
				ex = prefix +"." + fn;
				break;
				
		}
		
		return ex;
	}
	
	public Aggregates(String str){
		//wouldn't work for '_' in column names
		String f[] = str.split("_");
		if(f.length == 2){
			//function for entire relation
			isEntire = true;
			function = f[0];
			column   = f[1];
			gv       = "entire";
		} else if(f.length == 3){
			//function for group
			isEntire = false;
			gv       = f[0];
			function = f[1];
			column   = f[2];
		}
	}

}
