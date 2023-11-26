grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

IMPORT : 'import';
BOOL : 'true'|'false';
CLASS : 'class';
PUBLIC : 'public';
EXTENDS: 'extends';
MAIN : 'main';
IF: 'if';
ELSE: 'else';
WHILE: 'while';
NEW: 'new';
THIS: 'this';
INT: 'int';
BOOLEAN: 'boolean';
VOID: 'void';
STATIC: 'static';

NUM : ([0-9] | [1-9][0-9]*) ;
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

WS :  ([ \t\n\r\f]+ | '//'(.)*?'\n' | '/*' (.)*? '*/')-> skip;

program 
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration
    : IMPORT importClass+=ID ( '.' importClass+=ID )* ';'
    ;

classDeclaration
    : CLASS name=ID ( EXTENDS extendedClass=ID )? '{' varDeclarations methods  '}'
    ;

methods
    : ( methodDeclaration )*
    ;


varDeclaration
    : type var=ID ';'
    ;

methodDeclaration locals [boolean isStatic = false;]
    : mainDeclaration {$isStatic = true;}
    |(PUBLIC)? type methodName=ID '(' (params)? ')' '{' varDeclarations statements (returnStatement)? '}'
    ;


varDeclarations
    : ( varDeclaration)*
    ;

statements
    : ( statement )*
    ;

returnStatement:
    'return' expression ';'
;
params
    : param (',' param)*
    ;

param
    : type var=ID
    ;

mainDeclaration
    : (PUBLIC)?      STATIC VOID methodName=MAIN '(' typeName=ID '[' ']' name=ID ')' '{' varDeclarations statements '}'
    ;

type locals [boolean isArray = false, boolean isClass = false;]
    : name=INT '[' ']' {$isArray = true;}
    | name=BOOLEAN
    | name=INT
    | name=VOID
    | name=ID {$isClass = true;}
    ;

statement
    : '{' ( statement )* '}'                                #Block
    | IF '(' expression ')' statement ELSE statement        #If
    | WHILE '(' expression ')' statement                    #While
    | expression ';'                                        #Line
    | variable '=' expression ';'                             #Assign
    | variable '[' expression ']' '=' expression ';'          #ArrayAssign
    ;

expression:
     '(' expression ')'                                             #Parenthesis
    | expression '[' expression ']'                                 #Array
    | expression '.' 'length'                                       #Length
    | expression '.' method=ID '(' args ')'                         #FunctionCall
    |'!' expression                                                 #Neg
    | expression op=( '*' | '/' ) expression                        #ArithmeticBinaryOP
    | expression op=( '+' | '-' ) expression                        #ArithmeticBinaryOP
    | expression op='<'  expression                                 #LogicalBinaryOP
    | expression op='&&' expression                                 #LogicalBinaryOP
    | NEW INT '[' expression ']'                                    #NewArray
    | NEW type '(' ')'                                              #New
    | value=BOOL                                                    #Boolean
    | name=variable                                                 #Var
    | value=NUM                                                     #Int
    | THIS                                                          #This
    ;

args
    : ( expression ( ',' expression )* )?
    ;

variable: var=ID                    #VariableId
;
