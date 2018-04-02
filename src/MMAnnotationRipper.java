import java.io.*;
import java.util.*;

/**
 * Created by altered on 3/17/18.
 */
public class MMAnnotationRipper {
    public class Annotation {
        private String term = "";                   //term in paper
        private String conceptID = "";              //UMLS ID
        private String goID = "";                   //GO ID
        private String conceptName = "";            //UMLS name
        private String conceptPrefName = "";        //UMLS preferred name
        private int startIndex = 0;                 //starting index of term in paper
        private int endIndex = 0;                   //ending index of term in paper
        private String phrase = "";                 //phrase where annotation was located

        //getters/setters
        public String getTerm() { return term; }
        public String getConceptID() { return conceptID; }
        public String getGoID() { return goID; }
        public String getConceptName() { return conceptName; }
        public String getConceptPrefName() { return conceptPrefName; }
        public String getPhrase() { return phrase; }
        public int getStartIndex() { return startIndex; }
        public int getEndIndex() { return endIndex; }


        private void setTerm(String term) { this.term = term; }
        private void setConceptID(String conceptID) { this.conceptID = conceptID; }
        private void setGoID(String goID) { this.goID = goID; }
        private void setConceptName(String conceptName) { this.conceptName = conceptName; }
        private void setConceptPrefName(String conceptPrefName) { this.conceptPrefName = conceptPrefName; }
        private void setStartIndex(int startIndex) { this.startIndex = startIndex; }
        private void setEndIndex(int endIndex) { this.endIndex = endIndex; }
        private void setPhrase(String phrase) { this.phrase = phrase; }
    }

    public static void main(String[] args) {
        //name of directory containing annotation files
        File inputDirectory = new File("batch");
        //name of directory containing raw text files
        File textDirectory = new File("convertedRawText");
        //name of directory for output
        File outputDirectory = new File("output");
        //name of file containing mappings from CUIDs to GO IDs
        File cuidFile = new File("MRCONSO.RRF");
        MMAnnotationRipper mmAnnotationRipper = new MMAnnotationRipper();
        //iterate through annotation files
        for(File infile: inputDirectory.listFiles()){
            String infilename = infile.getName().substring(0,8);
            //TEST
//            if(infilename.equals("15492776")) {
                List<Annotation> annotations = mmAnnotationRipper.annotationRip(infile);
                //iterate through text files
                for (File textfile : textDirectory.listFiles()) {
                    //if same file...
                    if (infilename.equals(textfile.getName().substring(0, 8))) {
                        //match annotations to text to retrieve index information
                        annotations = mmAnnotationRipper.annotationMatch(annotations, textfile, cuidFile);
                        //write the tsv output file
                        writeOut(infilename, outputDirectory, annotations);
                    }
                }
//            }
        }

        boolean bool = true;
    }

    private List<Annotation> annotationRip(File anoFile){
        List<Annotation> annotations = new ArrayList<>();
        String line = "", phrase, conceptname, prefname, conceptid;
        int cindex, colonindex, begparenthesisindex, secondbegparenindex, endparenthesisindex, squarebracketindex;
        try{
            Scanner scan = new Scanner(anoFile);
            //find the next phrase
            while(scan.hasNextLine()){
                line = scan.nextLine();
                if(line.contains("Phrase:")){
                    phrase = line.substring(8);
                    if(scan.hasNextLine()){
                        line = scan.nextLine();
                        //check for any annotations for the current phrase
                        while(line.contains("Meta Mapping") && scan.hasNextLine()){
                            line = scan.nextLine();
                            //create a new annotation
                            Annotation anno = new Annotation();
                            //get the indices of the concept ID, concept name, and concept preferred name
                            cindex = line.indexOf("C");
                            colonindex = line.indexOf(":");
                            begparenthesisindex = line.indexOf("(");
                            secondbegparenindex = line.indexOf("(", begparenthesisindex+1);
                            endparenthesisindex = line.indexOf(")");
                            squarebracketindex = line.indexOf("[");
                            //check for blank cases and assign blank values if needed
                            if(cindex == -1){
                                conceptid = "";
                            }
                            else{
                                conceptid = line.substring(cindex, cindex+8);
                            }
                            if(colonindex == -1){
                                conceptname = "";
                            }
                            //case of no preferred name
                            if(!(colonindex == -1) && !(squarebracketindex == -1) && begparenthesisindex == -1){
                                conceptname = line.substring(colonindex+1, squarebracketindex-1);
                                prefname = "";
                            }
                            else if(!(colonindex == -1) && squarebracketindex == -1 && begparenthesisindex == -1){
                                conceptname = "";
                                prefname = "";
                            }
                            //case of nested parenthesis for preferred name
                            else if(secondbegparenindex != -1){
                                conceptname = line.substring(colonindex+1, begparenthesisindex-1);
                                prefname = line.substring(begparenthesisindex+1, secondbegparenindex);
                            }
                            //case of parentheses in concept name
                            else if(colonindex+1 >= begparenthesisindex-1){
                                conceptname = line.substring(begparenthesisindex, squarebracketindex-1);
                                prefname = "";
                            }
                            //regular case
                            else{
                                conceptname = line.substring(colonindex+1, begparenthesisindex-1);
                                prefname = line.substring(begparenthesisindex+1, endparenthesisindex);
                            }
                            //set annotation values
                            anno.setConceptID(conceptid);
                            anno.setConceptName(conceptname);
                            anno.setConceptPrefName(prefname);
                            anno.setPhrase(phrase);
                            //add to list
                            annotations.add(anno);
                            if(scan.hasNextLine()){
                                line = scan.nextLine();
                            }
                        }
                    }
                }
            }
            scan.close();
        }catch (FileNotFoundException ex){
            System.out.println("Error: File " + anoFile.getName() + " not found.");
        }catch (StringIndexOutOfBoundsException ex){
            System.out.println(line);
        }
        return annotations;
    }

    private List<Annotation> annotationMatch(List<Annotation> annotations, File textfile, File cuidFile){
        String conceptname, phrase, prevphrase = "", text = "", cuid, goid = "";
        List<String> lines = new ArrayList<>();
        int startIndex, endIndex, lastStartIndex = -1, barindex;
        try{
            //pull all text data for file
            Scanner scan = new Scanner(textfile);
            while(scan.hasNextLine()){
                text += scan.nextLine() + "\n";
            }
            text = text.toLowerCase();
            scan = new Scanner(cuidFile);
            while(scan.hasNextLine()){
                lines.add(scan.nextLine());
            }
            scan.close();
            //iterate through annotations
            for(Annotation annotation: annotations){
                //pull the current concept name and phrase associated with the annotation
                conceptname = annotation.getConceptName();
                phrase = annotation.getPhrase();
                phrase = phrase.toLowerCase();
                //check if concept name exists, if so check if it is literally located within the phrase
                if(!conceptname.equals("") && !phrase.equals(prevphrase)){
                    //phrase contains concept name
                    if(phrase.contains(conceptname.toLowerCase())){
                        startIndex = text.indexOf(phrase, lastStartIndex+1) + phrase.indexOf(conceptname.toLowerCase());
                        endIndex = startIndex + conceptname.length();
                    }
                    //phrase does not contain concept name
                    else{
                        startIndex = text.indexOf(phrase, lastStartIndex+1);
                        endIndex = startIndex + phrase.length();
                    }
                }
                //previous phrase and current phrase do not match
                else if(!phrase.equals(prevphrase)){
                    startIndex = text.indexOf(phrase, lastStartIndex+1);
                    endIndex = startIndex + phrase.length();
                }
                //previous phrase and current phrase do match
                else{
                    startIndex = lastStartIndex;
                    endIndex = startIndex + phrase.length();
                }
                //assign the annotation start and end indices
                annotation.setStartIndex(startIndex);
                annotation.setEndIndex(endIndex);
                //keep track of the prev start index and phrase
                lastStartIndex = startIndex;
                prevphrase = phrase;

                cuid = annotation.getConceptID();
                for(String line: lines){
                    if(line.contains(cuid) && line.contains(conceptname)){
                        barindex = line.indexOf("|||");
                        goid = line.substring(barindex+3, barindex+13);
                        annotation.setGoID(goid);
                        break;
                    }
                }
            }
        }catch (FileNotFoundException ex){
            System.out.println("Error: File " + textfile.getName() + " not found.");
        }
        return annotations;
    }

    /***
     * writeOut writes a tab-separated file of annotations for each paper.
     * @param filename - name of output file
     * @param directory - directory for output file
     * @param annotations - list of annotations for the file
     */
    private static void writeOut(String filename, File directory, List<Annotation> annotations){
        //setup file
        filename = directory + "/" + filename + ".tsv";
        File filen = new File(filename);

        //create and write to file
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filen, false)))) {
            writer.println("StartIndex\tEndIndex\tGO:ID\tConceptName\tPreferredName\tCUID");
            for (Annotation a : annotations){
                writer.println(a.getStartIndex() + "\t" + a.getEndIndex() + "\t" + a.getGoID()
                        + "\t" + a.getConceptName() + "\t" + a.getConceptPrefName() + "\t" + a.getConceptID());
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Error: File not found; could not append.");
            System.out.println("File path: " + filen);
        } catch (UnsupportedEncodingException ex) {
            System.out.println("Error: Unsupported encoding; could not append.");
            System.out.println("File path: " + filen);
        } catch (IOException ex) {
            System.out.println("Error: IO Exception; could not append.");
            System.out.println("File path: " + filen);
        }
    }
}
