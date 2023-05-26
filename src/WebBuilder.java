import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.MissingFormatArgumentException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebBuilder {
    File HTMLPreDir;
    File baseDir;
    File stubDir;
    File outDir;
    String curBaseName;
    String curBasePrettyName;

    public static void main(String[] args) throws IOException {
        File pd = new File("HTMLPre");
        File od = new File("output");
        File test = new File("HTMLPre/bases/index.html");
        System.out.println(test.getName());
        WebBuilder wb = new WebBuilder(pd, od);
        wb.start();
    }

    public WebBuilder(File preHTMLDir, File outDirectory) {
        HTMLPreDir = preHTMLDir;
        baseDir = new File(preHTMLDir.getName() + "/bases");
        stubDir = new File(preHTMLDir.getName() + "/stubs");
        outDir = outDirectory;
    }

    public void start() throws IOException {

        File[] filesInBase = baseDir.listFiles(file -> file.getName().endsWith(".html"));

        if (filesInBase == null) {
            System.out.println("There aint no files in " + baseDir);
        }

        else {
            for (File file : filesInBase) {
                curBaseName = file.getName().replace(".html", "");
                curBasePrettyName = matchName(Files.readString(file.toPath())).replace(".html", "");
                String fileText = replaceStubs(file);
                writeToOutput(fileText);
            }
        }

        try {
            copyDirectory(new File(HTMLPreDir.getName() + "/other"), outDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String replaceStubs(File file) throws IOException {

        String fileText = Files.readString(file.toPath());
        fileText = replaceVars(fileText, curBaseName, curBasePrettyName);

        ArrayList<String> stubNames = matchStubs(fileText);
        for (String stubName : stubNames) {
            System.out.println("Processing stub: " + stubName);
            if (stubName.startsWith("||navBuilder||")) {
                System.out.println("found the builder");
                fileText = navBuilder(fileText, stubName);
            }
            else {
                System.out.println("went the normal path");
                fileText = replaceStub(fileText, stubName);
            }
            //System.out.println("stub: " + stubName + " : " + fileText);
        }
        return fileText;
    }

    public String navBuilder(String fileText, String stubName) throws IOException {

        String cleanedName = stubName.substring(14);
        File navFile = new File(stubDir + "/" + cleanedName + ".html");

        File[] filesInBase = baseDir.listFiles(file -> file.getName().endsWith(".html"));
        assert filesInBase != null;
        File[] orderedFileList = new File[filesInBase.length];
        for (File file : filesInBase) {
            orderedFileList[matchOrder(Files.readString(file.toPath()))] = file;
        }
        StringBuilder navs = new StringBuilder();

        for(File file : orderedFileList) {
            String navText = Files.readString(navFile.toPath());
            navText = replaceVars(navText, file.getName().replace(".html", ""), matchName(Files.readString(file.toPath())));
            navText = replaceLines(navText, file);
            ArrayList<String> navStubs = matchStubs(navText);

            for (String navStub : navStubs) {
                    //System.out.println("navstub: " + navStub);
                String[] stubSides = navStub.split("%%\\|\\|%%");
                //System.out.println("Stubsides: " + stubSides[0] + " || " + stubSides[1]);
                if (file.getName().replace(".html", "").equals(curBaseName)) {
                    navText = navText.replace("<!-- {{" + navStub + "}} -->", stubSides[0]);
                }
                else {
                    navText = navText.replace("<!-- {{" + navStub + "}} -->", stubSides[1]);
                }
            }
            navs.append(navText);
        }
        return fileText.replace("<!-- {{" + stubName + "}} -->", navs);
    }

    public void writeToOutput(String completedText) throws FileNotFoundException {
        try(PrintWriter out = new PrintWriter(outDir + "/" + curBaseName + ".html")) {
            out.print(completedText);
        }
    }

    public String replaceVars(String inputText, String name, String prettyName) {
        String replacedText = inputText;

        while (replacedText.contains("$&PRETTYNAME&")) {
            replacedText = replacedText.replace("$&PRETTYNAME&", prettyName);
        }
        while (replacedText.contains("$&NAME&")) {
            replacedText = replacedText.replace("$&NAME&", name);
        }

        return replacedText;
    }

    public String replaceLines(String inputText, File curFile) throws IOException {
        int numLines = matchName(Files.readString(curFile.toPath())).length() + 2;
        String outputText = inputText;
        while (outputText.contains("<!-- $&LINES& -->")) {
            System.out.println("diD THE lines file: " + curFile.getName() + " | " + numLines);
            outputText = outputText.replace("<!-- $&LINES& -->", "-".repeat(numLines));
        }
        return outputText;
    }

    public String replaceStub(String text, String stubName) throws IOException {
        File stubFile = new File(stubDir + "/" + stubName + ".html");
        return text.replace("<!-- {{" + stubName + "}} -->", replaceStubs(stubFile));
    }

    public ArrayList<String> matchStubs(String fileText) throws IOException {
        // Compile regular expression
        final Pattern pattern = Pattern.compile("<!-- +\\{\\{([^}]*)\\}\\} -->", Pattern.CASE_INSENSITIVE);
        // Match regex against input
        final Matcher matcher = pattern.matcher(fileText);
        // Use results...
        ArrayList<String> matchedStrings = new ArrayList<>();
        while (matcher.find()) {
            matchedStrings.add(matcher.group(1));
        }

        return matchedStrings;
    }

    public static String matchName(String fileText) {
        final Pattern pattern = Pattern.compile("<!-- +\\[\\[([^]]*)\\]\\] -->", Pattern.CASE_INSENSITIVE);
        // Match regex against input
        final Matcher matcher = pattern.matcher(fileText);
        // Use results...
        if (!matcher.find()) {
            throw new MissingFormatArgumentException("No name in: " + fileText);
        }
        return matcher.group(1);
    }

    public static int matchOrder(String fileText) {
        final Pattern pattern = Pattern.compile("<!-- \\|\\|(\\d)\\|\\| -->", Pattern.CASE_INSENSITIVE);
        // Match regex against input
        final Matcher matcher = pattern.matcher(fileText);
        // Use results...
        if (!matcher.find()) {
            throw new MissingFormatArgumentException("No order in: " + fileText);
        }
        return Integer.parseInt(matcher.group(1));
    }

    public void copyDirectory(File sourceLocation , File targetLocation)
            throws IOException {

        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists()) {
                targetLocation.mkdir();
            }

            String[] children = sourceLocation.list();
            for (String child : children) {
                copyDirectory(new File(sourceLocation, child),
                        new File(targetLocation, child));
            }
        } else {

            InputStream in = new FileInputStream(sourceLocation);
            OutputStream out = new FileOutputStream(targetLocation);

            // Copy the bits from instream to outstream
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        }
    }


}
