import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.*;


import com.nesstar.api.*;
import com.nesstar.api.search.SearchQuery;
import com.nesstar.api.search.SearchResult;
import com.nesstar.api.search.SearchResultItem;

import javax.xml.xpath.XPathExpressionException;

public final class NesstarDownloader {
   private final NesstarDB nesstarDB;
   private final Server server;
   private static final Logger logger = Logger.getLogger(NesstarDownloader.class.getName());

   public NesstarDownloader(URI serverURI) throws IOException {
      nesstarDB = NesstarDBFactory.getInstance();
      server = nesstarDB.getServer(serverURI);
   }

   public String getListText() throws NotAuthorizedException, IOException {
      NesstarList<Study> allStudies = server.getBank(Study.class).getAll();
      StringBuilder studyLabelList = new StringBuilder();
      for (Study study : allStudies) {
         studyLabelList.append(study.getLabel());
         studyLabelList.append(System.getProperty("line.separator"));
      }
      return studyLabelList.toString();
   }

   public int getNumberOfStudies() throws NotAuthorizedException, IOException {
      int n = 0;
      NesstarList<Study> allStudies = server.getBank(Study.class).getAll();
      n = allStudies.size();
      return n;
   }

   private ArrayList findAllStudies(NesstarTreeNode node, ArrayList studies) throws NotAuthorizedException, IOException {
      if (node instanceof ResourceLinkTreeNode) {
         ResourceLinkTreeNode resourceLinkNode = (ResourceLinkTreeNode) node;
         NesstarTreeObject  obj = resourceLinkNode.getResource();
         String id = obj.getId();

         if (id.startsWith("cora-")) {
            if (obj instanceof Study) {
               Study study = (Study) obj;
               studies.add(study);
            }

         }
      } else if (node instanceof FolderTreeNode) {
         FolderTreeNode folderNode = (FolderTreeNode) node;
         List<NesstarTreeNode> children = folderNode.getChildren();
         for (NesstarTreeNode child : children) {
            findAllStudies(child, studies);
         }
      }
      return studies;
   }

   private void writeFiles(ArrayList studies)  {
      logger.info("Number of studies " + studies.size());
      //Create list of all studies
      File listOfAllStudies = new File("list_of_all_studies2.txt");
      if (listOfAllStudies.exists()) {
         listOfAllStudies.delete();
      }
      FileWriter writerListOfStidies = null;
      boolean flagListFiles = false;
      try {
         listOfAllStudies.createNewFile();
         writerListOfStidies = new FileWriter(listOfAllStudies);
         flagListFiles = true;
      } catch (IOException e) {
         logger.log(Level.WARNING, "Cannot create " + "list_of_all_studies.txt", e );
      }

      int n = 0;

      for (Object study: studies) {
         Study st = (Study) study;
         if (flagListFiles) {
            try {
               writerListOfStidies.write(st.getId() + "\n");
            } catch (IOException e) {
               logger.log(Level.WARNING,"Cannot write to " + "list_of_all_studies.txt", e);
            }
         }
         n++;
         //Create xml file
         try {
            ResultStream rs = st.getDDI();
            File targetFile = new File("CORA/" + st.getId() + ".xml");
            java.nio.file.Files.copy(
                    rs,
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            logger.log(Level.SEVERE, "Cannot get or save ddi " + st.getId() ,  e);
         }
         //Create SPSS file
         try {
            if (st.hasData()) {
               ResultStream rsSPSS = st.download(FileFormat.SPSS, null);
               File targetFileSPSS = new File("CORA/" + st.getId() + ".zip");

               java.nio.file.Files.copy(
                       rsSPSS,
                       targetFileSPSS.toPath(),
                       StandardCopyOption.REPLACE_EXISTING);
            } else {
               System.out.println(st.getId());
            }
         } catch (Exception e) {
            logger.log(Level.SEVERE,"Cannot download or save " + st.getId() ,  e);
         }
      }
      if (flagListFiles) {
         try {
            writerListOfStidies.flush();
            writerListOfStidies.close();
         } catch (IOException e) {
            logger.log(Level.WARNING, "Cannot close " + "list_of_all_studies.txt", e);
         }
      }
   }

   public void getTreeRoot() {
      try {
         FolderTreeNode ftn = server.getTreeRoot();
         List<NesstarTreeNode> children = ftn.getChildren();

         ArrayList<Integer> studies = new ArrayList<Integer>();

         for (NesstarTreeNode child : children) {
            FolderTreeNode folderNode = (FolderTreeNode) child;
            List<NesstarTreeNode> children2 = folderNode.getChildren();
            for (NesstarTreeNode child2 : children2) {
               if (child2.getLabel().equals("Public Opinion Polls")) {
                  //FolderTreeNode targetFolder = (FolderTreeNode) child2;


                  studies = findAllStudies(child2, studies);
                  break;
               }
            }
         }
         logger.info("Studies " + studies.size());
         writeFiles(studies);
      } catch (Exception e) {
         logger.log(Level.SEVERE, "getTreeRoot exception ", e);
      }
   }

   ///////////////////////////////////////////////////////////////////////////////////////////////////

   private static ArrayList<String> readStudiesId(String coraIdsFile) throws FileNotFoundException {
      ArrayList<String> coraIds = new ArrayList<>();
      File coraFile = new File(coraIdsFile);
      Scanner myReader = new Scanner(coraFile);
      while (myReader.hasNextLine()) {
         String id = myReader.nextLine();
         coraIds.add(id);
      }
      myReader.close();

      return coraIds;
   }
   private void downloadXML(Study study) {
      //Create xml file
      try {
         ResultStream rs = study.getDDI();
         File targetFile = new File("CORA/" + study.getId() + ".xml");
         java.nio.file.Files.copy(
                 rs,
                 targetFile.toPath(),
                 StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
         logger.log(Level.SEVERE, "Cannot get or save ddi " + study.getId() ,  e);
      }
   }

   private  void downloadSPSS(Study study) {
      //Create SPSS file
      try {
         if (study.hasData()) {
            ResultStream rsSPSS = study.download(FileFormat.SPSS, null);
            File targetFileSPSS = new File("CORA/" + study.getId() + ".zip");

            java.nio.file.Files.copy(
                    rsSPSS,
                    targetFileSPSS.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
         } else {
            System.out.println(study.getId());
         }
      } catch (Exception e) {
         logger.log(Level.SEVERE,"Cannot download or save " + study.getId() ,  e);
      }
   }

   public static void main(String[] args) throws FileNotFoundException {
      System.out.println("Program is starting");

      try {

         Handler fileHandler  = null;
         NesstarDownloader nesstarDownloader;

         fileHandler  = new FileHandler("./nesstarDownloader.log" );
         fileHandler.setLevel(Level.ALL);


         logger.addHandler(fileHandler);
         logger.setUseParentHandlers(false);
         SimpleFormatter formatter = new SimpleFormatter();
         fileHandler.setFormatter(formatter);
         logger.info("Started");

         ArrayList<String> coraIds = readStudiesId("./cora_all.txt");
         nesstarDownloader = new NesstarDownloader(new URI("http://odesi2.scholarsportal.info"));
         Bank bank = nesstarDownloader.server.getBank(Study.class);
         for (String id: coraIds) {
            Study study = (Study) bank.get(id);
            if (study != null) {
               nesstarDownloader.downloadXML(study);
               nesstarDownloader.downloadSPSS(study);
            } else {
               logger.log(Level.SEVERE, "No study with id: " + id);
            }
         }

         //nesstarServerStudyLister = new NesstarStudyLister(new URI("http://nesstar-demo.nsd.uib.no"));

         //SearchQuery search = new SearchQuery();
         //search.include("cora-egim-E-2001-update");
         //search.addReturnType(Study.class);
         //search.setNumberOfHits(10000);
         //SearchResult r = nesstarServerStudyLister.server.search(search);
         //List<SearchResultItem> sr = r.getListOfHits();
         //System.out.println( "result of search " + sr.size() + " items");
         //for (SearchResultItem item: sr) {
         //   System.out.println(item.getId() + " " + item.getLabel());
         //   Study st = (Study) item;

         //   ResultStream d = st.getDDI();
         //   File targetFile = new File( st.getId() + ".xml");
         //   java.nio.file.Files.copy(
         //           d,
         //           targetFile.toPath(),
         //           StandardCopyOption.REPLACE_EXISTING);
            //if (item.getObject() instanceof Study) {
            //   System.out.println(item.getId() + " " + item.getLabel());
               //((Study) item.getObject()).download(FileFormat.SPSS, null);
            //}
         //}



         //nesstarDownloader.getTreeRoot();


         //String studyListText = nesstarDemoServerStudyLister.getListText();
         //System.out.println(studyListText);
      } catch (Exception ioe) {
         logger.log(Level.SEVERE,"Error: ",  ioe);
      }
      logger.info("Ended");
   }
}
