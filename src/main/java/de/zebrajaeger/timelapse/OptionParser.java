package de.zebrajaeger.timelapse;

import org.apache.commons.cli.*;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OptionParser {

    public static String createDirName() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        return df.format(LocalDateTime.now());
    }

    public static Timelapse parse(String[] args) {
        Options options = createOptions();

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption('h')) {
                printHelp(options);
                return null;
            }

            long n = Long.parseLong(cmd.getOptionValue('n', "60"));
            long p = Long.parseLong(cmd.getOptionValue('p', "1000"));
            String t = cmd.getOptionValue('p', ".");
            String e = cmd.getOptionValue('e');
            boolean o = cmd.hasOption('o');

            String dirName = createDirName();
            File ep = null;
            if (e != null) {
                ep = o ? new File(e) : new File(e, dirName);
            }

            return Timelapse.builder()
                    .numberOfPictures(n)
                    .targetPath(o ? new File(t) : new File(t, dirName))
                    .tempPath(ep)
                    .periodTimeMs(p)
                    .build();

        } catch (ParseException e) {
            // Print the help message
            System.err.println(e.getMessage());
            printHelp(options);
        }
        return null;
    }

    private static Options createOptions() {
        Options options = new Options();

        Option numberOfPictures = new Option("n", "numberOfPictures", true, "Number of pictures (optional, default is 60)");
        numberOfPictures.setRequired(false);
        options.addOption(numberOfPictures);

        Option periodTimeMs = new Option("p", "periodTimeMs", true, "Number of pictures (optional, default is 1000");
        periodTimeMs.setRequired(false);
        options.addOption(periodTimeMs);

        Option targetPath = new Option("t", "targetPath", true, "Target directory where the pictures are stored");
        targetPath.setRequired(false);
        options.addOption(targetPath);

        Option tempPath = new Option("e", "tempPath", true, "Temp directory for pictures (optional)");
        tempPath.setRequired(false);
        options.addOption(tempPath);

        Option addTimestamp = new Option("o", "noTimestamp", false, "Don't add timestamp to temp- and target-path");
        addTimestamp.setRequired(false);
        options.addOption(addTimestamp);

        Option help = new Option("h", "help", false, "Show this text");
        help.setRequired(false);
        options.addOption(help);
        return options;
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Canon Timelapse", options);
    }
}
