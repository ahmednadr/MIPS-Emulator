package com.company;

import javax.swing.*;
//import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.io.File;

public class Main {
    static int [] mainMem = new int[2048]; //word addressable memory of size 2048
    int apc=0;// number of inst. in file
    int pc=0; // program counter
    enum writestate{
        WRITESTATE,// mem write
        READSTATE, // mem read
        REGSTATE // write back
    }
    int [] regfile = new int[33]; // reg. 0->32
    int clock = 1;
    writestate s; // todo use structs
    int decodeReg = 0; // reg between fetch and decode (el answer ely tl3 mn fetch)
    int [] executeReg = new int[7];// reg bet. decode and execute
    int [] memReg = new int[2];// execute .. mem
    int [] wbReg = new int[2];// mem .. wb
    int [] states = new int[5];// inst. number inside stage
    boolean flush = false;
    public Main() {}

    public void writeReg(int value , int index , writestate s){
        if (index!=0 && s!=writestate.WRITESTATE){
            regfile[index]=value;
        }
    }

    public int[] mem (int value , int index , writestate s){
        int [] x;
        try {
            if (s == writestate.WRITESTATE) {// sw
                mainMem[index] = value;

            }
            if (s == writestate.READSTATE) {// lw
                value= mainMem[index];
            }
        }catch(ArrayIndexOutOfBoundsException e){
            System.out.println("index bigger than memory size");
        }
        x= new int[]{value, index};
        return x;
    }

    public static void main (String [] args){
        Main cpu = new Main();

        JFileChooser f = new JFileChooser();
        int responce=f.showOpenDialog(null);
        String s; // filepath
        if (responce==JFileChooser.APPROVE_OPTION){
            s = f.getSelectedFile().getAbsolutePath();
        }else{
            System.out.println("you didn't select a file");
            return;
        }

        cpu.loadfile(s);// s is the file path
        cpu.run();
        System.out.println(cpu);
    }

    public void loadfile(String filepath){
        File file = new File(filepath);
//        String s = "ADDi r1 , r0 , -3  \n addi r2 , r0 , 4 \n add r6 , r3 , r3 \n sw r1 , 1 ( r0 )";
        Scanner asm = null; //read text file (replace s with file)
        try {
            asm = new Scanner(file);
        } catch (FileNotFoundException e) {
            System.out.println("please select a text file");
        }
        ArrayList <String> labels= new ArrayList<String>(); //array list to store labels ex: (loop1 : )
        // we can use mapping instead of arraylist
        while (asm.hasNextLine() && apc<=1024){ // do while the text file still has next line
            int i = 0;
            String input = asm.nextLine().trim(); //trim removes spaces at the start and the end of a line
            String [] words = input.split("\\s+"); //split the line at every space(or more than one space) and put in list of words
            if (words[1].equals(":") ){ //if the second word of the line is (:) this means there is a label we need to add to labels list
                labels.add(words[0]); //add label to our list
                labels.add(apc+""); //add label pc value
                i = 2; //the instruction starts at index two (index zero is the label itself and index one is the : )
            }
            int x=-1; // initiate opcode value
            switch (words[i].toLowerCase()){
                case "add" :  x=0; break;
                case "sub":   x=1; break;
                case "muli":  x=2; break;
                case "addi" : x=3; break;
                case "bne" :  x=4; break;
                case "andi" : x=5; break;
                case "ori" :  x=6; break;
                case "j" :    x=7; break;
                case "sll" :  x=8; break;
                case "srl" :  x=9; break;
                case "lw" :   x=10;break;
                case "sw" :   x=11;break;
                default:System.out.println("error : no such instruction supported");break;
            }
            if (x==7){ // todo jump labels below
                //jump label ex: j loop1 next word is the label
                String p = "";
                int l;
                try {
                     p = labels.get(labels.indexOf(words[i+1])+1); // get the label pc to be added to the instruction
                     l =Integer.valueOf(p);
                }catch(Exception e){
                    System.out.println("this jump label doesnt exist"); // exception if the label wasn't found in the list
                    int e3 = Integer.valueOf(words[i+1]);
                    if(e3>=-131072 && e3<=131072){
                        e3=e3 & 0b00001111111111111111111111111111;// to make sure of thee size so it doesn't mess up the inst.
                    }else{
                        System.out.println("exceeded the range of jump value");
                        break;
                    }
                    l=e3;
                }
                x=x<<28; //opcode
                x+=l;
            }else if(x==0||x==1){ // add ,sub
                // r type , shift = 0 , ex: add r1,r2,r3
                int e1= Integer.valueOf(words[i+1].substring(1));// r1 -> 1 -> 001
                e1=e1<<23;//r1
                int e2= Integer.valueOf(words[i+3].substring(1));
                e2=e2<<18;//r2
                int e3= Integer.valueOf(words[i+5].substring(1));
                e3=e3<<13;//r3
                x=x<<28; //opcode
                x=x+e1+e2+e3;//instruction
            }else if(x==10 ||x==11){// lw ,sw
                // i type , shift = 0 , ex: lw r1 , imm ( r2 )
                int e1= Integer.valueOf(words[i+1].substring(1));
                e1=e1<<23;//r1
                int e2= Integer.valueOf(words[i+5].substring(1));
                e2=e2<<18;//r2
                int e3= Integer.valueOf(words[i+3]);
                if(e3>=-131072 && e3<=131072){
                    e3=e3 & 0b00000000000000111111111111111111;
                }else{
                    System.out.println("exceeded the range of imm value");
                    break;
                }
                x=x<<28; //opcode
                x=x+e1+e2+e3;//instruction
            }else if(x==8||x==9){// srl , sll , r3 = 0 , srl r1 , r2 , shamt
                int e1= Integer.valueOf(words[i+1].substring(1));
                e1=e1<<23;//r1
                int e2= Integer.valueOf(words[i+3].substring(1));
                e2=e2<<18;//r2
                int e3= Integer.valueOf(words[i+5]);
                if(e3>=-8191 && e3<=8191){
                    e3=e3 & 0b00000000000000000001111111111111;
                }
                x=x<<28; //opcode
                x=x+e1+e2+e3;//instruction
            }else{
                int e1 = Integer.valueOf(words[i+1].substring(1));
                e1 = e1 << 23;//r1
                int e2 = Integer.valueOf(words[i+3].substring(1));
                e2 = e2 << 18;//r2
                int e3 = Integer.valueOf(words[i+5]);
                if(e3>=-131072 && e3<=131072){
                    e3=e3 & 0b00000000000000111111111111111111;
                }else{
                    System.out.println("exceeded the range of imm value");
                    break;
                }
                x = x << 28; //opcode
                x = x+e1+e2+e3;//instruction
            }
            mainMem[apc]=x;
            apc++;
        }
    }

    public void runsec(){
        int decodeReg = 0;
        int [] excuteReg = new int[7];
        while(pc<=6){
            decodeReg=fetch();
            excuteReg=decode(decodeReg);
            memReg=execute(excuteReg[0],excuteReg[1],excuteReg[2],excuteReg[3],excuteReg[4],excuteReg[5],excuteReg[6]);
            wbReg=mem(memReg[0],memReg[1],s);
            writeReg(wbReg[0],wbReg[1],s );
        }
    }


    public void run (){

        String [] names = {"fetch","decode","execute","mem","wb"};
        boolean done = false; // 5lst el fetching
        boolean donemem=false;
        boolean donewb = false;
        boolean doneex = false;
        boolean donede = false;
        System.out.println(Arrays.toString(names));

        while (!donewb){
            if(clock%2!=0){
                states[0]=0;
                if (done){
                    donede=true;
                }
                if (donede){
                    doneex=true;
                }
                if (clock>=7){
                    if (!donewb){
                        writeReg(wbReg[0],wbReg[1],s );
                        states[4]=states[3]; 
                    }else{
                        states[4]=0;
                        donewb=true;
                    }
                }

                if (donemem){
                   donewb=true;
                }

                if(!done){
                decodeReg=fetch();
                if (decodeReg==0){
                    done = true;
                }else{
                    states[0]=pc;// jumps and BNE
                    }
                }
                states[3]=0;// starts in even cycle and ends in odd

            }else{

                if(clock>=6){
                    if (!donemem){
                        wbReg=mem(memReg[0],memReg[1],s);
                        states[3]=states[2];
                    }else{
                        states[3]=0;
                        donemem = true;
                    }
                }

                if (doneex){
                    donemem=true;
                }

                if(clock>=4){
                    if(!flush){
                    if (!doneex){
                memReg=execute(executeReg[0],executeReg[1],executeReg[2],executeReg[3],executeReg[4],executeReg[5],executeReg[6]);
                states[2]=states[1];
                }else{
                    states[2]=0;
                    doneex = true;
                }
                    }else {
                        flush=false;
                        System.out.println("flush");
                        decodeReg=0;// bwady ll decode 0
                        executeReg=new int[]{0,0,0,0,0,0,0};
                        states[1]=0;
                        states[2]=0;
                        states[0]=0;
                        states[4]=0;
                        System.out.println("cycle " +clock+" : "+Arrays.toString(states));
                        clock++;
                        continue;// btla3ny l awl el loop
                    }
            }

                if(clock>=2){
                if (!donede){
                executeReg=decode(decodeReg);
                states[1]=states[0];
                }else{
                    states[1]=0; // if run enough it while go through the < apc condition
                    donede = true;
                }
            }

                states[0]=0;
                states[4]=0;
            }
            System.out.println("cycle " +clock+" : "+Arrays.toString(states));

            clock++;
        }
    }

    public int fetch() {
        int instruction = 0;
        instruction = mainMem[pc];//fetch
        System.out.println(" fetch parameter : "+pc);
        pc++;
        return instruction;
    }


    public int[] decode(int instruction) {
        System.out.println("input parameter to decode :"+instruction);
        int opcode = 0;  // bits31:27
        int rs = 0;      // bits26:24
        int rt = 0;      // bit23:21
        int rd = 0;      // bits20:18
        int shamt = 0;   // bits17:10
        int imm = 0;     // bits20:0
        int address = 0; // bits26:0

        opcode = instruction & 0b11110000000000000000000000000000;
        opcode = opcode >>> 28;
        rs = instruction & 0b00001111100000000000000000000000;
        rs = rs >> 23;
        rt = instruction & 0b00000000011111000000000000000000;
        rt = rt >> 18;
        rd = instruction & 0b00000000000000111110000000000000;
        rd = rd >> 13;
        shamt = instruction & 0b00000000000000000001111111111111;
        imm = instruction & 0b00000000000000111111111111111111;
        address = instruction & 0b00001111111111111111111111111111;

        int [] x = {opcode,rs,rt,rd,shamt,address,imm};
        return x;
    }

    public int[] execute(int op,int r1,int r2,int r3,int shift ,
                              int address ,int imm){
        int value=0; // result of operation to be written in either memory or reg
        int index=r1; // index of reg where the value would be written
        s=writestate.REGSTATE;
        System.out.println("input parameters for execute :"+ " opcode : "+op + ","
        +" r1 : "+r1+","+
                " r2 : "+r2+","+
                " r3 : "+r3+","+
                " shamt : "+shift+","+
                " address : "+address+","+
                " immediate : "+imm);

        switch (op){
            case 0 : value=regfile[r2]+regfile[r3]; break;
            case 1 : value=regfile[r2]-regfile[r3]; break;
            case 2 :
                if(imm>=131072){ // sign extension
                imm = imm | 0b11111111111111000000000000000000;
            }
            value=regfile[r2]*imm; break;
            case 3 :
                if(imm>=131072){
                imm = imm | 0b11111111111111000000000000000000;
            }
            value=regfile[r2]+imm; break;
            case 4 :
                if(imm>=131072){
                imm = imm | 0b11111111111111000000000000000000;
            }
                System.out.println( regfile[r1]!=regfile[r2]);
            if(regfile[r1]!=regfile[r2]){pc=--pc+imm;flush=true;value=regfile[r1];}break;// 3shan howa byzed mareten f 2alelna mara
            case 5 :
                if(imm>=131072){
                imm = imm | 0b11111111111111000000000000000000;
            }
            value=regfile[r2]&imm;break;
            case 6 :
                if(imm>=131072){
                imm = imm | 0b11111111111111000000000000000000;
            }
            value=regfile[r2]|imm;break;
            case 7 : pc=(pc & 0b11110000000000000000000000000000)+address-2;value=regfile[r1];flush=true;break;
            case 8 : value=regfile[r2]<<shift;break;
            case 9 : value=regfile[r2]>>shift;break;
            case 10: index=regfile[r2]+imm;s=writestate.READSTATE;break; // lw r2 , 3 ( r2 )
            case 11: value=regfile[r1];index=regfile[r2]+imm;s=writestate.WRITESTATE;break;
            default: System.out.println("no such instruction exists");break;
    }
    int [] x = {value,index};
    return x;
    }
    @Override
    public String toString() {
        return "Main{" +
                "R0=" + '0' +
                ", regfile=" + Arrays.toString(regfile) +
                ", clock=" + clock +"/n"+
                ",memory"+ Arrays.toString(mainMem)+
                '}';
    }
}