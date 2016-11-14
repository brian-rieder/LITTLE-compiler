import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;
import java.lang.*;
import java.io.*;

class AntlrGlobalListener extends MicroBaseListener {
    private int blockCounter;
    public List<SymbolTable> allSymbolTables = new ArrayList<SymbolTable>();
    private List<IRList> allIRLists = new ArrayList<IRList>();
    private Hashtable<String,String> varTypeTable = new Hashtable<String,String>();
    private Hashtable<String,String> regTypeTable = new Hashtable<String,String>();

    // if blocks
    private int labelCounter;
    private int regCounter;
    private Hashtable<String,String> logicOpTable = new Hashtable<String,String>();
    private Stack<String> labelStack = new Stack<String>();
    private Stack<String> exitStack = new Stack<String>();

    public AntlrGlobalListener() {
        this.blockCounter = 1;
        this.regCounter   = 1;
        this.labelCounter = 1;
        populateLogicalOps();
    }



    /****** Scope stuff 
    /****** (creates new symbol tables and adds it to list of all symbol tables) ******/

    @Override
    public void enterPgm_body(MicroParser.Pgm_bodyContext ctx) {
        SymbolTable global = new SymbolTable("GLOBAL");
        this.allSymbolTables.add(global); 
    }

    @Override
    public void exitPgm_body(MicroParser.Pgm_bodyContext ctx) {

        for (int i = 0; i < this.allSymbolTables.size(); i++) {
            Set<String> set = new HashSet<String>();

            for (int x = 0; x < this.allSymbolTables.get(i).objectList.size(); x++) {
                if (set.contains(this.allSymbolTables.get(i).objectList.get(x).varName) == true) {
                    System.out.println("DECLARATION ERROR " + this.allSymbolTables.get(i).objectList.get(x).varName);
                    return;
                }
                set.add(this.allSymbolTables.get(i).objectList.get(x).varName);
            }
        }

        // Print output
        // for (int i = 0; i < this.allSymbolTables.size(); i++) {
        //     System.out.println("Symbol table " + this.allSymbolTables.get(i).scope);
        //     for (int x = 0; x < this.allSymbolTables.get(i).objectList.size(); x++) {
        //         this.allSymbolTables.get(i).objectList.get(x).print();
        //     }

        //     if (i +1 < this.allSymbolTables.size()) { // remove trailing extra newline
        //         System.out.println();
        //     }
        // }

        System.out.println(";IR code");
        for(IRList ilist : allIRLists) {
            for(IRNode inode : ilist.getList()) {
                System.out.println(";" + inode.getOpcode() + " " + inode.getOperand1() 
                    + " " + inode.getOperand2() + " " + inode.getResult());
            }
        }

       
        TinyGenerator tg = TinyGenerator()
        
        
    }

    @Override 
    public void enterFunc_decl(MicroParser.Func_declContext ctx) { 
        SymbolTable func = new SymbolTable(ctx.getChild(2).getText());
        this.allSymbolTables.add(func); 

    }

    private void populateLogicalOps() {
        logicOpTable.put(">",  "LE");
        logicOpTable.put("<",  "GE");
        logicOpTable.put(">=", "LT");
        logicOpTable.put("<=", "GT");
        logicOpTable.put("!=", "EQ");
        logicOpTable.put("=",  "NE");
    }

    private ArrayList<String> tokenizeConditional(String expression) {
        ArrayList<String> tokenized_list = new ArrayList<String>();
        StringTokenizer strtok = new StringTokenizer(expression, "<=>=!=<>=", true);
        tokenized_list.add(strtok.nextToken());
        if (strtok.countTokens() > 2) {
            tokenized_list.add(strtok.nextToken() + strtok.nextToken());
        } else {
            tokenized_list.add(strtok.nextToken());
        }
        tokenized_list.add(strtok.nextToken());
        return tokenized_list;
    }

    private void pushLabelStack() {
        labelStack.push("label" + Integer.toString(labelCounter));
        labelCounter += 1;
    }

    private void pushExitStack() {
        exitStack.push("label" + Integer.toString(labelCounter));
        labelCounter += 1;
    }

    @Override 
    public void enterIf_stmt(MicroParser.If_stmtContext ctx) { 
        SymbolTable ifst = new SymbolTable("BLOCK " + blockCounter++);
        this.allSymbolTables.add(ifst); 

        IRList ir = new IRList();
        String expr = ctx.getChild(2).getText();
        ArrayList<String> tokenized_list = tokenizeConditional(expr);
        if(expr.equals("TRUE")) {
            // untested by step 5
            RPNTree.regnum++;
            ir.appendNode("STOREI", "1", "", "$T" + Integer.toString(RPNTree.regnum));
            regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "INT");
            RPNTree.regnum++;
            ir.appendNode("STOREI", "1", "", "$T" + Integer.toString(RPNTree.regnum));
            regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "INT");
            ir.appendNode("NE", "$T" + Integer.toString(RPNTree.regnum - 1), 
                "$T" + Integer.toString(RPNTree.regnum), labelStack.peek());
        }
        else {
            ShuntingYardConverter converter = new ShuntingYardConverter();
            ArrayList<String> rpn_list = converter.expressionParse(tokenized_list.get(2));
            RPNTree rpn_tree = new RPNTree();
            rpn_tree = rpn_tree.parseRPNList(rpn_list);
            if(varTypeTable.get(tokenized_list.get(0)).equals("INT")) {
                ir = rpn_tree.rhsIRGen(ir, rpn_tree);
                if(rpn_tree.getLeftChild() == null && rpn_tree.getRightChild() == null) {
                    rpn_tree.regnum++;
                    ir.appendNode("STOREI", tokenized_list.get(2), "", "$T" + Integer.toString(rpn_tree.regnum));
                    regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "INT");
                } 
            }
            else {
                ir = rpn_tree.rhsIRGenFloat(ir, rpn_tree);
                if(rpn_tree.getLeftChild() == null && rpn_tree.getRightChild() == null) {
                    rpn_tree.regnum++;
                    ir.appendNode("STOREF", tokenized_list.get(2), "", "$T" + Integer.toString(rpn_tree.regnum));
                    regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "FLOAT");
                } 
            }
            // previous approach: doesn't account for calculations on RHS
            // if(varTypeTable.get(tokenized_list.get(0)).equals("INT")) {
            //     ir.appendNode("STOREI", tokenized_list.get(2), "", "$T" + Integer.toString(RPNTree.regnum));
            // }
            // else {
            //     ir.appendNode("STOREF", tokenized_list.get(2), "", "$T" + Integer.toString(RPNTree.regnum));
            // }
        }
        pushLabelStack();
        pushExitStack();
        String logic_op = logicOpTable.get(tokenized_list.get(1));
        ir.appendNode(logic_op, tokenized_list.get(0), "$T" + Integer.toString(RPNTree.regnum), labelStack.peek());

        allIRLists.add(ir);
    }

    @Override 
    public void enterElse_part(MicroParser.Else_partContext ctx) { 
        IRList ir = new IRList();
        ir.appendNode("JUMP", "", "", exitStack.peek());
        ir.appendNode("LABEL", "", "", labelStack.pop());
        if (!ctx.getText().isEmpty()) { // don't want to add else block if unused
            SymbolTable elst = new SymbolTable("BLOCK " + blockCounter++);
            this.allSymbolTables.add(elst);             

            pushLabelStack();
            // pushExitStack();
            String expr = ctx.getChild(2).getText();
            // ArrayList<String> tokenized_list = tokenizeConditional(expr);
            if(expr.equals("TRUE")) {
                RPNTree.regnum++;
                ir.appendNode("STOREI", "1", "", "$T" + Integer.toString(RPNTree.regnum));
                regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "INT");
                RPNTree.regnum++;
                ir.appendNode("STOREI", "1", "", "$T" + Integer.toString(RPNTree.regnum));
                regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "INT");
                ir.appendNode("NE", "$T" + Integer.toString(RPNTree.regnum - 1), 
                    "$T" + Integer.toString(RPNTree.regnum), labelStack.peek());
                regTypeTable.put(labelStack.peek(), "INT");
            }
            else { // i.e., not TRUE
                // untested by step 5
                ArrayList<String> tokenized_list = tokenizeConditional(expr);
                RPNTree.regnum++; 
                if(varTypeTable.get(tokenized_list.get(0)).equals("INT")) {
                    ir.appendNode("STOREI", tokenized_list.get(2), "", "$T" + Integer.toString(RPNTree.regnum));
                    regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "INT");
                }
                else {
                    ir.appendNode("STOREF", tokenized_list.get(2), "", "$T" + Integer.toString(RPNTree.regnum));
                    regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "FLOAT");
                }
                String logic_op = logicOpTable.get(tokenized_list.get(1));
                ir.appendNode(logic_op, tokenized_list.get(0), 
                    "$T" + Integer.toString(RPNTree.regnum), labelStack.peek());
                regTypeTable.put(labelStack.peek(), "FLOAT");
            }
        }
        allIRLists.add(ir);
    }

    @Override
    public void exitIf_stmt(MicroParser.If_stmtContext ctx) {
        IRList ir = new IRList();
        ir.appendNode("LABEL", "", "", exitStack.pop());
        allIRLists.add(ir);
    }

    @Override 
    public void enterDo_while_stmt(MicroParser.Do_while_stmtContext ctx) { 
        SymbolTable dowhl = new SymbolTable("BLOCK " + blockCounter++);
        this.allSymbolTables.add(dowhl);

        IRList ir = new IRList();
        pushLabelStack();
        pushExitStack();
        ir.appendNode("LABEL", "", "", labelStack.peek());
        allIRLists.add(ir);
    }

    @Override
    public void exitDo_while_stmt(MicroParser.Do_while_stmtContext ctx) {
        IRList ir = new IRList();
        String expr = ctx.getChild(5).getText();
        ArrayList<String> tokenized_list = tokenizeConditional(expr);
        RPNTree.regnum++;
        if(varTypeTable.get(tokenized_list.get(0)).equals("INT")) {
            ir.appendNode("STOREI", tokenized_list.get(2), "", "$T" + Integer.toString(RPNTree.regnum));
            regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "INT");
        }
        else {
            ir.appendNode("STOREF", tokenized_list.get(2), "", "$T" + Integer.toString(RPNTree.regnum));
            regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "FLOAT");
        }
        String logic_op = logicOpTable.get(tokenized_list.get(1));
        ir.appendNode(logic_op, tokenized_list.get(0), "$T" + Integer.toString(RPNTree.regnum), exitStack.peek());
        ir.appendNode("JUMP", "", "", labelStack.pop());
        ir.appendNode("LABEL", "", "", exitStack.pop());
        allIRLists.add(ir);
    }

    @Override
    public void enterRead_stmt(MicroParser.Read_stmtContext ctx) {
        IRList ir = new IRList();
        ir.appendNode("READI", "", "", ctx.getChild(2).getText());
        allIRLists.add(ir);
    }

    /** Variable declarations 
    /** (creates symbol objects for variables and adds them to current symbol table) **/

    @Override
    public void enterString_decl(MicroParser.String_declContext ctx) {
        SymbolObject newSymbolObject = new SymbolObject("STRING", ctx.getChild(1).getText(), ctx.getChild(3).getText());
        allSymbolTables.get(allSymbolTables.size()-1).addObject(newSymbolObject);
    }

    @Override 
    public void enterVar_decl(MicroParser.Var_declContext ctx) { 
        String varNames = ctx.getChild(1).getText();
        varNames = varNames.replaceAll(";","");

        for (String varName : varNames.split(",")) {
            SymbolObject newSymbolObject = new SymbolObject(ctx.getChild(0).getText(), varName);
            allSymbolTables.get(allSymbolTables.size()-1).addObject(newSymbolObject);
            varTypeTable.put(varName, ctx.getChild(0).getText());
        }     
  }

    @Override 
    public void enterParam_decl(MicroParser.Param_declContext ctx) { 
        SymbolObject newSymbolObject = new SymbolObject(ctx.getChild(0).getText(), ctx.getChild(1).getText());
        allSymbolTables.get(allSymbolTables.size()-1).addObject(newSymbolObject);
        // possibly need to handle multiple variables seperately
    }

    @Override
    public void enterAssign_expr(MicroParser.Assign_exprContext ctx) {
        // converts to postfix
        ShuntingYardConverter converter = new ShuntingYardConverter();
        ArrayList<String> rpn_list = converter.expressionParse(ctx.getChild(2).getText()); 

        // creates abstract syntax tree from list
        RPNTree rpn_tree = new RPNTree();
        rpn_tree = rpn_tree.parseRPNList(rpn_list); 
       
        IRList ir = new IRList();
        if(varTypeTable.get(ctx.getChild(0).getText()).equals("INT")) {
            ir = rpn_tree.rhsIRGen(ir, rpn_tree);
            if(rpn_tree.getLeftChild() == null && rpn_tree.getRightChild() == null) {
                rpn_tree.regnum++;
                ir.appendNode("STOREI", ctx.getChild(2).getText(), "", "$T" + Integer.toString(rpn_tree.regnum));
                regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "INT");
            }
            ir.appendNode("STOREI", "$T"+Integer.toString(rpn_tree.regnum), "", ctx.getChild(0).getText());
            regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "INT");
            regTypeTable.put(ctx.getChild(0).getText(), "INT");
        }
        else {
            ir = rpn_tree.rhsIRGenFloat(ir, rpn_tree);
            if(rpn_tree.getLeftChild() == null && rpn_tree.getRightChild() == null) {
                rpn_tree.regnum++;
                ir.appendNode("STOREF", ctx.getChild(2).getText(), "", "$T" + Integer.toString(rpn_tree.regnum));
                regTypeTable.put("$T" + Integer.toString(RPNTree.regnum), "FLOAT");
            }
            ir.appendNode("STOREF", "$T"+Integer.toString(rpn_tree.regnum), "", ctx.getChild(0).getText());
                regTypeTable.put(ctx.getChild(0).getText(), "FLOAT");
        }
        // for(IRNode inode : ir.getList()) {
        //     System.out.println(inode.getOpcode() + " " + inode.getOperand1() 
        //         + " " + inode.getOperand2() + " " + inode.getResult());
        // }
        allIRLists.add(ir);
    }

    @Override
    public void enterWrite_stmt(MicroParser.Write_stmtContext ctx) {
        IRList ir = new IRList();
        if(varTypeTable.get(ctx.getChild(2).getText()).equals("INT")) {
            ir.appendNode("WRITEI", ctx.getChild(2).getText(), "", "");
        }
        else {
            ir.appendNode("WRITEF", ctx.getChild(2).getText(), "", "");
        }
        // System.out.println(ir.getNode().getOpcode() + " " + ir.getNode().getOperand1() 
        //         + " " + ir.getNode().getOperand2() + " " + ir.getNode().getResult());
        allIRLists.add(ir);
    }
    
}