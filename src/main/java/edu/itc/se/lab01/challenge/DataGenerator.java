package edu.itc.se.lab01.challenge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DataGenerator {

    static List<String> generateVocabulary(int size) {
        List<String> words = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            char c1 = (char) ('a' + (i / 676));
            char c2 = (char) ('a' + ((i / 26) % 26));
            char c3 = (char) ('a' + (i % 26));
            words.add("w" + c1 + c2 + c3);
        }
        return words;
    }

    public static void generateFiles(Path dir, int fileCount, int wordsPerFile, List<String> vocab) throws IOException {
        Files.createDirectories(dir);
        Random rnd = new Random(42);

        for (int i = 0; i < fileCount; i++) {
            Path file = dir.resolve("file" + i + ".txt");
            try (var writer = Files.newBufferedWriter(file)) {
                for (int w = 0; w < wordsPerFile; w++) {
                    int idx = rnd.nextInt(vocab.size());
                    writer.write(vocab.get(idx));
                    writer.write(" ");
                    if ((w + 1) % 20 == 0) {
                        writer.newLine();
                    }
                }
            }
        }
        System.out.printf("Generated %d files in %s (~%d words each)%n", fileCount, dir, wordsPerFile);
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "./data");
        int fileCount = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        int wordsPerFile = args.length > 2 ? Integer.parseInt(args[2]) : 50_000;

        List<String> vocab = generateVocabulary(2000);
        generateFiles(dir, fileCount, wordsPerFile, vocab);
    }
}
