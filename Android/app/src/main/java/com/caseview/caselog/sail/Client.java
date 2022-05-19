package com.caseview.caselog.sail;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;

import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.util.StringTokenizer;
import java.util.Date;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class Client implements Serializable {

    private static Context context;

    public static void getMainContext(Context context){Client.context = context;}

    static public boolean loggedin = false;
    static HashMap<String, String> profileCache = null;
    static Vector<CaseCount> caseCounts = null;
    static ArrayList<CaseRecord> caseRecords = new ArrayList<>();
    static Map<Date, ArrayList<CaseRecord>> caseGroup = new HashMap<>();
    static Map<Date, ArrayList<CaseRecord>> dateGroup = new HashMap<>();

    private final String USER_ACCESS_PATH = "http://192.168.43.23:8080/CaseLogServer/UserAccess";
    private final String PROFILE_PATH = "http://192.168.43.23:8080/CaseLogServer/Profile";
    private final String PAYMENT_PATH = "http://192.168.43.23:8080/CaseLogServer/Payment";
    private final String CASE_NUMBER_PATH = "http://192.168.43.23:8080/CaseLogServer/CaseNumber";
    private final String CASE_INFO_PATH = "http://192.168.43.23:8080/CaseLogServer/CaseInfo";
    private final String UPDATE_PROFILE_PATH = "http://192.168.43.23:8080/CaseLogServer/UpdateProfile";
    private final String DUPLICATE_ID_PATH = "http://192.168.43.23:8080/CaseLogServer/DuplicateId";



    public String serverConnection(String path, String query, boolean inputEnabled) {
        String dataString = null;

        String pathLocal = path;
        String queryLocal = query;
        String urlSpecifier = "";
        if (queryLocal != null && queryLocal != "") {
            urlSpecifier = pathLocal + queryLocal;
        } else {
            urlSpecifier = pathLocal;
        }

        URL url = null;
        URLConnection con = null;
        try {
            url = new URL(urlSpecifier);
            con = url.openConnection();
            InputStreamReader inR = null;

            if (inputEnabled) {
                try {
                    inR = new InputStreamReader(new BufferedInputStream(con.getInputStream()));
                    String data = "";
                    int c = 0;
                    while ((c = inR.read()) != -1) {
                        data += (char) c;
                    }
                    dataString = data;
                } finally {
                    try {
                        inR.close();
                    } catch (IOException ex) {}
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return dataString;
    }

    public boolean checkID(String id) {
        String query = SqlBuilder.buildIdSql(id);
        String profileString = serverConnection(USER_ACCESS_PATH, query, true);
        if (!bothEquals(profileString, "")) {
            char testChar = profileString.charAt(0);
            if (testChar == 'T') {
                setProfileCache(profileString.substring(1));
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean checkPass(String userPass) {

        String dbPass = getProfileCache().get("pass");
        if (bothEquals(dbPass, userPass)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean checkDuplicateId(String idToCheck) {
        String query = SqlBuilder.buildDuplicateIdSql(idToCheck);
        String duplicateIdChar = serverConnection(DUPLICATE_ID_PATH, query, true);
        System.out.println(duplicateIdChar);
        char testChar = duplicateIdChar.charAt(0);
        if (testChar == 'T') {
            return true;
        }
        return false;
    }

    public void setProfile(String query) {
        String stackTrace = serverConnection(PROFILE_PATH, query, true);
    }

    public void setProfileWithCache(String id, String pass, String access, String type, String name, String father, String home,
            String village, String subDis, String district, String phone1, String phone2, String phone3, String phone4, String phone5, String selectedCases) {
        setProfileCache(id, pass, "", "", name, father, home, village, subDis, district, phone1, phone2, "", "", "", "");
        String query = SqlBuilder.buildProfileSql(getProfileCache());
        setProfile(query);
    }

    public void initClient() {
        Thread t = new Thread(){
            @Override
            public void run() {
                getCaseNumbers(null);
                synchronizeSelectedCases();
                getCaseInfo(SqlBuilder.buildCaseInfoSql(getCaseCounts()));
            }
        };
        t.start();
    }

    public void updateProfile() {
                String query = SqlBuilder.buildUpdateProfileSql(getProfileCache());
                String stackTrace = serverConnection(UPDATE_PROFILE_PATH, query, true);
    }

    public void updateSelectedCases() {
        Log.d("CaseLog", "updateSelectedCase is called");
        String numbers = "";
        Vector<CaseCount> caseCountsLocal = Client.caseCounts;
        Log.d("CaseLog", "updateSelectedCases(): size of casecounts : "+caseCounts.size());
        for (Iterator ite = caseCountsLocal.iterator(); ite.hasNext();) {
            CaseCount cc = (CaseCount) ite.next();
            if (cc.isSelected()) {
                Log.d("CaseLog", "updateSelectedCases(): " +cc.isSelected());
                numbers += cc.buildSelectedCasesString();
                numbers += ")";
                Log.d("CaseLog", "updateSelectedCases(): " +numbers);
            }
        }
        Log.d("CaseLog", "Client.updateSelectedCase(): "+ numbers);
        getProfileCache().put("selectedCases", numbers);
        Log.d("CaseLog", "updateSelectedCase is called from last");
    }

    public void synchronizeSelectedCases() {
        HashMap<String, String> profileCacheLocal = getProfileCache();
        ArrayList<SelectedCases> selectedCases = new ArrayList<>();
        String selectedCasesString = profileCacheLocal.get("selectedCases");
        if (bothEquals(selectedCasesString, "null") || bothEquals(selectedCasesString, "Empty") || bothEquals(selectedCasesString, "")) {
            return;
        }
        populateSelectedCases(selectedCasesString, selectedCases);

        Vector<CaseCount> caseCountsLocal = getCaseCounts();

        for (Iterator ite = selectedCases.iterator(); ite.hasNext();) {
            SelectedCases sel = (SelectedCases) ite.next();
            for (Iterator ite2 = caseCountsLocal.iterator(); ite2.hasNext();) {
                CaseCount cc = (CaseCount) ite2.next();
                if (bothEquals(sel.courtTable, cc.courtTable)) {
                    cc.setSelected(true);
                    cc.setSelectedNumbers(sel.selectedNumbers);
                    continue;
                }
            }
        }
    }

    private void populateSelectedCases(String selectedCasesString, ArrayList<SelectedCases> selectedCases) {
        StringTokenizer court_numberTokenizer = new StringTokenizer(selectedCasesString, ")");
        while (court_numberTokenizer.hasMoreTokens()) {
            String court_number = court_numberTokenizer.nextToken();
            StringTokenizer courtTokenizer = new StringTokenizer(court_number, "?");

            String courtTable = courtTokenizer.nextToken();
            SelectedCases sel = new SelectedCases(courtTable);

            String numberString = courtTokenizer.nextToken();
            StringTokenizer numberTokenizer = new StringTokenizer(numberString, ".");
            while (numberTokenizer.hasMoreTokens()) {
                sel.selectedNumbers.add(numberTokenizer.nextToken());
            }
            selectedCases.add(sel);
        }
    }

    public HashMap<String, String> getProfileCache() {
        return profileCache;
    }

    public void setProfileCache(HashMap<String, String> profileCache, boolean ambigous) {
        Client.profileCache = profileCache;
    }

    void setProfileCache(String profileString) {
        HashMap<String, String> profile = new HashMap<>();
        StringTokenizer profileTokenizer = new StringTokenizer(profileString, ",");
        profile.put("id", profileTokenizer.nextToken());
        profile.put("pass", profileTokenizer.nextToken());
        profile.put("access", profileTokenizer.nextToken());
        profile.put("type", profileTokenizer.nextToken());
        profile.put("name", profileTokenizer.nextToken());
        profile.put("father", profileTokenizer.nextToken());
        profile.put("home", profileTokenizer.nextToken());
        profile.put("village", profileTokenizer.nextToken());
        profile.put("subDis", profileTokenizer.nextToken());
        profile.put("district", profileTokenizer.nextToken());
        profile.put("phone1", profileTokenizer.nextToken());
        profile.put("phone2", profileTokenizer.nextToken());
        profile.put("phone3", profileTokenizer.nextToken());
        profile.put("phone4", profileTokenizer.nextToken());
        profile.put("phone5", profileTokenizer.nextToken());
        profile.put("selectedCases", profileTokenizer.nextToken());

        profileCache = profile;
    }

    void setProfileCache(String id, String pass, String access, String type, String name, String father, String home,
            String village, String subDis, String district, String phone1, String phone2, String phone3, String phone4, String phone5, String selectedCases) {
        if (bothEquals(access, "")) {
            access = "No Access Key Set";
        }
        if (bothEquals(type, "")) {
            type = "No Type Set";
        }
        if (bothEquals(name, "")) {
            name = "No Name Set";
        }
        if (bothEquals(father, "")) {
            father = "No Father Name Set";
        }
        if (bothEquals(home, "")) {
            home = "No Home Set";
        }
        if (bothEquals(village, "")) {
            village = "NO Village Set";
        }
        if (bothEquals(subDis, "")) {
            subDis = "NO Subdistrict Set";
        }
        if (bothEquals(district, "")) {
            district = "NO district Set";
        }
        if (bothEquals(phone1, "")) {
            phone1 = "NO Other Phone Set";
        }
        if (bothEquals(phone2, "")) {
            phone2 = "NO Other Phone Number Set";
        }
        if (bothEquals(phone3, "")) {
            phone3 = "NO Other Phone Number Set";
        }
        if (bothEquals(phone4, "")) {
            phone4 = "NO Other Phone Number Set";
        }
        if (bothEquals(phone5, "")) {
            phone5 = "NO Other Phone Number Set";
        }
        if (bothEquals(selectedCases, "")) {
            selectedCases = "Empty";
        }

        HashMap<String, String> profile = new HashMap<>();
        profile.put("id", id);
        profile.put("pass", pass);
        profile.put("access", access);
        profile.put("type", type);
        profile.put("name", name);
        profile.put("father", father);
        profile.put("home", home);
        profile.put("village", village);
        profile.put("subDis", subDis);
        profile.put("district", district);
        profile.put("phone1", phone1);
        profile.put("phone2", phone2);
        profile.put("phone3", phone3);
        profile.put("phone4", phone4);
        profile.put("phone5", phone5);
        profile.put("selectedCases", selectedCases);

        profileCache = profile;
    }

    public ArrayList<PaymentRecord> getPayment() {
        String query = getIdForPayment();
        String monthFeeString = serverConnection(PAYMENT_PATH, query, true);
        ArrayList<PaymentRecord> payment = new ArrayList<>();
        StringTokenizer monthFeeTokenizer = new StringTokenizer(monthFeeString, "/");
        while (monthFeeTokenizer.hasMoreTokens()) {
            String monthFee = monthFeeTokenizer.nextToken();
            StringTokenizer monthTokenizer = new StringTokenizer(monthFee, ",");
            String month = monthTokenizer.nextToken();
            String fee = monthTokenizer.nextToken();
            payment.add(new PaymentRecord(month, fee));
        }
        setPaymentRecords(payment);
        return payment;
    }

    private static ArrayList<PaymentRecord> paymentRecords = new ArrayList<>();

    public static ArrayList<PaymentRecord> getPaymentRecords() {
        return paymentRecords;
    }

    public static void setPaymentRecords(ArrayList<PaymentRecord> paymentRecords) {
        Client.paymentRecords = paymentRecords;
    }

    public ArrayList<PaymentRecord> getPaymentInThread(){
        Thread t = new Thread(){
            @Override
            public void run() {
                getPayment();
            }
        };
        t.start();
        return getPaymentRecords();
    }

    private String getIdForPayment(){
        HashMap<String,String> profileCache = getProfileCache();
        String id = "";
        if(profileCache!= null && !profileCache.isEmpty()){
            id = profileCache.get("id");
        }

        String query = "";
        if(id != null)
            query = SqlBuilder.buildPaymentSql(id);
        Log.d("CaseLog", "Client.getIdForPayment(): " +id);
        return query;
    }

    public Vector<CaseCount> getCaseNumbers(String query) {
        String court_caseString = serverConnection(CASE_NUMBER_PATH, query, true);
        Vector<CaseCount> caseCounts = new Vector<>();
        StringTokenizer court_caseTokenizer = new StringTokenizer(court_caseString, ")");
        while (court_caseTokenizer.hasMoreTokens()) {
            String court_case = court_caseTokenizer.nextToken();
            StringTokenizer courtTokenizer = new StringTokenizer(court_case, "-");
            String courtTable = courtTokenizer.nextToken();
            String caseNumberString = courtTokenizer.nextToken();
            caseCounts(courtTable, caseNumberString, caseCounts);
        }
        setCaseCounts(caseCounts);
        return caseCounts;
    }

    public Vector<CaseCount> getSyncronizedCaseNumbers() {
        Thread t = new Thread(){
            @Override
            public void run() {
                getCaseNumbers(null);
                synchronizeSelectedCases();
            }
        };
        t.start();
        // At this stage caseCounts is set from within getCaseNumbers(). So now we can return caseCounts by getCaseCounts().
        return getCaseCounts();
    }

    private void caseCounts(String courtTable, String caseNumberString, Vector caseCounts) {
        int counter[] = new int[99];
        int minYear = 2010;
        int yearInt = 0;

        StringTokenizer caseNumberTokenizer = new StringTokenizer(caseNumberString, ",");
        while (caseNumberTokenizer.hasMoreTokens()) {
            String caseNumber = caseNumberTokenizer.nextToken();
            //note: if any record of 'number' column in '***District' is null then
            //a null pointer exception is thrown here. because 'null' is s string. fix it.
            StringTokenizer yearTokenizer = new StringTokenizer(caseNumber, "/");
            String number = yearTokenizer.nextToken();
            String year = yearTokenizer.nextToken();
            yearInt = Integer.parseInt(year);
            counter[yearInt - minYear]++;
        }
        caseCounts.add(new CaseCount(courtTable, counter));
    }

    public Vector<CaseCount> getCaseCounts() {
        return caseCounts;
    }

    public void setCaseCounts(Vector<CaseCount> caseCounts) {
        Client.caseCounts = caseCounts;
    }

    protected ArrayList getCaseInfo(String query) {
        String caseInfoString = serverConnection(CASE_INFO_PATH, query, true);
        ArrayList<CaseRecord> caseRecords = new ArrayList<CaseRecord>();
        StringTokenizer caseInfoTokenizer = new StringTokenizer(caseInfoString, ")");
        while (caseInfoTokenizer.hasMoreTokens()) {
            String court_recordString = caseInfoTokenizer.nextToken();
            StringTokenizer court_recordTokenizer = new StringTokenizer(court_recordString, "?");
            String courtName = court_recordTokenizer.nextToken();
            String caseRecordString = court_recordTokenizer.nextToken();
            buildCaseRecords(courtName, caseRecordString, caseRecords);
        }
        setCaseRecords(caseRecords);
        Map<Date, ArrayList<CaseRecord>> caseGroup = buildCaseGroup(caseRecords);
        setCaseGroup(caseGroup);
        Map<Date, ArrayList<CaseRecord>> dateGroup = buildDateGroup(caseRecords);
        int size = dateGroup.size();
        setDateGroup(dateGroup);
        return caseRecords;
    }

    private void buildCaseRecords(String courtName, String caseRecordString, ArrayList<CaseRecord> caseRecords) {
        StringTokenizer recordTokenizer = new StringTokenizer(caseRecordString, "'");
        while (recordTokenizer.hasMoreTokens()) {
            String aRecord = recordTokenizer.nextToken();
            CaseRecord cr = new CaseRecord(courtName);
            StringTokenizer fieldTokenizer = new StringTokenizer(aRecord, ".");
            for (int i = 0; i < 50; i++) {
                cr.fields.put(Integer.valueOf(i + 1), tokenizeBareField(fieldTokenizer.nextToken()));
            }
            caseRecords.add(cr);
        }
    }

    public ArrayList<CaseRecord> getCaseRecords() {
        return caseRecords;
    }

    public static void setCaseRecords(ArrayList<CaseRecord> caseRecords) {
        Client.caseRecords = caseRecords;
    }

    public Map<Date, ArrayList<CaseRecord>> buildCaseGroup(ArrayList<CaseRecord> caseRecords) {
        Map<Date, ArrayList<CaseRecord>> caseGroup = new HashMap<>();
        for (CaseRecord cr : caseRecords) {
            Date filing = cr.getField30AsDate();
            if (caseGroup.containsKey(filing)) {
                ArrayList<CaseRecord> list = caseGroup.get(filing);
                list.add(cr);
            } else {
                ArrayList<CaseRecord> list = new ArrayList<>();
                list.add(cr);
                caseGroup.put(filing, list);
            }
        }
        return caseGroup;
    }

    public Map<Date, ArrayList<CaseRecord>> buildDateGroup(ArrayList<CaseRecord> caseRecords) {
        Map<Date, ArrayList<CaseRecord>> dateGroup = new HashMap<>();
        for (CaseRecord cr : caseRecords) {
            for (int i = 30; i < 48; i += 2) {
                Date date = cr.getFieldsAsDate(i);
                if (date == null) {
                    continue;
                }

                if (dateGroup.containsKey(date)) {
                    ArrayList<CaseRecord> list = dateGroup.get(date);
                    list.add(cr);
                } else {
                    ArrayList<CaseRecord> list = new ArrayList<>();
                    list.add(cr);
                    dateGroup.put(date, list);
                }
            }
        }
        return dateGroup;
    }

    public Map<Date, ArrayList<CaseRecord>> getCaseGroup() {
        return caseGroup;
    }

    public void setCaseGroup(Map<Date, ArrayList<CaseRecord>> caseGroup) {
        Client.caseGroup = caseGroup;
    }

    public Map<Date, ArrayList<CaseRecord>> getDateGroup() {
        return dateGroup;
    }

    public void setDateGroup(Map<Date, ArrayList<CaseRecord>> dateGroup) {
        Client.dateGroup = dateGroup;
    }

    public boolean bothEquals(String s, String ss) {
        char[] cs = s.toCharArray();
        char[] css = ss.toCharArray();

        if (cs.length != css.length) {
            return false;
        }

        for (int i = 0; i < cs.length; i++) {
            if (cs[i] != css[i]) {
                return false;
            }
        }

        return true;
    }

    private String tokenizeBareField(String fieldToken) {
        String field = "null";
        if (fieldToken.length() > 1) {
            field = fieldToken.substring(1);
        }
        return field;
    }


    public boolean getLoggedin(){return loggedin;}

    public void setLoggedin(boolean loggedin) {
        loggedin = loggedin;
    }

    public void login() {
        loggedin = true;
        initClient();
    }

    public void logout() {
        loggedin = false;
        Thread t = new Thread(){
            @Override
            public void run() {
                updateProfile();
                deleteCacheLoginInfo();
            }
        };
        t.start();
    }

    void deleteCacheLoginInfo(){
        File loginInfo = new File(context.getFilesDir(), "LoginInfo.cl");
        if (loginInfo.exists()) loginInfo.delete();
    }

    public void cacheLoginInfo() {
        try {
            File loginInfo = new File(context.getFilesDir(), "LoginInfo.cl");
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(loginInfo));
            out.writeBoolean(loggedin);
            out.writeObject(getProfileCache());
            out.writeObject(getCaseCounts());
            out.writeObject(getCaseRecords());
            out.writeObject(getCaseGroup());
            out.writeObject(getDateGroup());
        } catch (Exception ex) {
            loggedin = false;
            ex.printStackTrace();
        }
    }


    public void getCachedLoginInfo() {
        try {
            File loginInfo = new File(context.getFilesDir(), "LoginInfo.cl");
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(loginInfo));
            loggedin = in.readBoolean();
            setProfileCache((HashMap<String, String>) in.readObject(), true);
            setCaseCounts((Vector<CaseCount>) in.readObject());
            setCaseRecords((ArrayList<CaseRecord>) in.readObject());
            setCaseGroup((Map<Date, ArrayList<CaseRecord>>) in.readObject());
            setDateGroup((Map<Date, ArrayList<CaseRecord>>) in.readObject());

        } catch (FileNotFoundException ex) {
            loggedin = false;
            setProfileCache(null, true);
        } catch (IOException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    public void onCaseSelectClose() {
        Log.d("CaseLog", "onCaseSelectClose is called");
        Thread t = new Thread(){
            @Override
            public void run() {
                Log.d("CaseLog", "onCaseSelectClose is called from inside run()");
                updateSelectedCases();
                getCaseInfo(SqlBuilder.buildCaseInfoSql(getCaseCounts()));
            }
        };
        t.start();
    }

    public void onCloseApplication() {
        Thread t = new Thread(){
            @Override
            public void run() {
                Log.d("CaseLog", "Client.onCloseApplication(): ");
                updateProfile();
                cacheLoginInfo();
            }
        };
        t.start();
    }

    public void print(String text){
        Log.d("CaseLog", text);
    }

}
