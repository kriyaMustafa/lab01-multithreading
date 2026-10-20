package edu.itc.se.lab01.challenge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.IOException;

public class DataGenerator {

   
    
    static List<String> WordGenerate() {
        List<String> word = new ArrayList<>();
        for(int i= 0;i<2000;i++){
            word.add("word"+i);
        }
        return word;
    }

    static void FileGenerate(Path dir , int file , int wordperfile, List<String> word) throws IOException{

        Random r = new  Random();

        for (int i = 0; i < file; i++) {
             Path filepath = dir.resolve("file" + i + ".txt");

             try(var w = Files.newBufferedWriter(filepath)){
                for (int j=0;j < wordperfile;j++){
                    int index = r.nextInt(word.size());
                    w.write(word.get(index));
                    w.write(" ");
                }
             }
             System.out.println("generate: "+ filepath);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: DataGenerator <dir> <files> <wordsPerFile>");
            return;
        }
        
        Path dir = Files.createDirectories(Path.of(args[0]));
        int file = Integer.parseInt(args[1]);
        int wordperfile = Integer.parseInt(args[2]);

        List<String> word = WordGenerate();
        FileGenerate(dir, file, wordperfile, word);


    }
}
