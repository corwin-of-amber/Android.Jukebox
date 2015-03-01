package nanohttpd;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import nanohttpd.NanoHTTPD.Response;
import nanohttpd.NanoHTTPD.ResponseException;



public class SplitBoundary {

	static class FilePart {
		enum State { HEADER, BODY }
		static final String HEADER_SEP = "\r\n\r\n";

		State state = State.HEADER;
		byte[] sep = HEADER_SEP.getBytes();
		int sepi = 0;
		
		ByteBuffer header = ByteBuffer.allocate(1024);
		NanoHTTPD.TempFile body;
		
		FileOutputStream fbody;
		BufferedOutputStream bbody;
		
		public FilePart(NanoHTTPD.TempFile bodyTempFile) throws IOException {
			body = bodyTempFile;
			try {
				fbody = (FileOutputStream)body.open();
				bbody = new BufferedOutputStream(fbody);
			}
			catch (Exception e) { throw new IOException("cannot open temp file for write"); }
		}
		
		String header() {
			byte[] bytes = new byte[header.position()];
			header.position(0);
			header.get(bytes);
			return new String(bytes);
		}
		
		void write(byte b) throws IOException {
			switch (state) {
			case HEADER: 
				header.put(b);
				if (b == sep[sepi]) sepi++;
				else { sepi = 0; if (b == sep[sepi]) sepi++; }
				if (sepi == sep.length) state = State.BODY;
				break;
			case BODY:
				bbody.write(b);
			}
		}
		
		void truncate(int nbytesBack) throws IOException {
			bbody.flush();
			FileChannel chan = fbody.getChannel();
			chan.truncate(Math.max(0, chan.size() - nbytesBack));
			fbody.close();
		}
	}
	
    public static List<FilePart> readAndSplitByBoundary(InputStream in, long size, String boundary,
    		NanoHTTPD.TempFileManager tfm)
    		throws Exception
    {
    	byte[] bytes = boundary.getBytes();
    	int index = 0;
    	byte[] buf = new byte[8192];
    	int rlen;
    	
    	List<FilePart> parts = new ArrayList<>();
    	FilePart part = null;
    	
    	while (size > 0 && (rlen = in.read(buf)) > 0) {
    		size -= rlen;
    		for (int i = 0; i < rlen; ++i) {
	    		//System.out.print(new String(buf));
	    		if (part != null) part.write(buf[i]);
	    		if (bytes[index] == buf[i]) {
	    			index++;
	    			if (index == bytes.length) {
	    				System.out.println("*****");
	    				if (part != null) {
	    					part.truncate(index + 4  /* trim trailing "\r\n--" */);
	    					parts.add(part);
	    					//System.out.println(part.header());
	    				}
	    				if (!(i == rlen-1 && size == 0))
	    					part = new FilePart(tfm.createTempFile());
	    				index = 0;
	    			}
	    		}
	    		else index = kmpBack(bytes, index, buf[i]);
    		}
    	}
    	
    	return parts;
    }
    
    private static int kmpBack(byte[] bytes, int index, byte next) {
    	int k = 0;
    	for (int i = 1; i < index; i++) {
    		if (bytes[i] == bytes[k]) k++;
    		else k = kmpBack(bytes, k, bytes[i]);
    	}
    	if (next == bytes[k]) k++; else k = 0;
    	return k;
    }

    /**
     * Decodes the Multipart Body data and put it into Key/Value pairs.
     */
    public static void decodeMultipartData(List<FilePart> parts, Map<String, String> parms,
                                     Map<String, String> files) throws ResponseException {
        try {
    		for (FilePart part : parts) {
    			BufferedReader in = new BufferedReader(new StringReader(part.header()));
                Map<String, String> item = new HashMap<String, String>();
                String mpline = in.readLine();
                while (mpline != null /*&& mpline.trim().length() > 0*/) {
                    int p = mpline.indexOf(':');
                    if (p != -1) {
                        item.put(mpline.substring(0, p).trim().toLowerCase(Locale.US), mpline.substring(p + 1).trim());
                    }
                    mpline = in.readLine();
                }
                String contentDisposition = item.get("content-disposition");
                if (contentDisposition == null) {
                    throw new ResponseException(Response.Status.BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html");
                }
                StringTokenizer st = new StringTokenizer(contentDisposition, ";");
                Map<String, String> disposition = new HashMap<String, String>();
                while (st.hasMoreTokens()) {
                    String token = st.nextToken().trim();
                    int p = token.indexOf('=');
                    if (p != -1) {
                        disposition.put(token.substring(0, p).trim().toLowerCase(Locale.US), token.substring(p + 1).trim());
                    }
                }
                String pname = disposition.get("name");
                pname = pname.substring(1, pname.length() - 1);

                String value = "";
                if (item.get("content-type") == null) {
                	FileInputStream f = new FileInputStream(part.body.getName());
                	byte[] valuebuf = new byte[(int)f.getChannel().size()];
                	value = new String(valuebuf, 0, f.read(valuebuf));
                	f.close();
                } else {
                    multiput(files, pname, part.body.getName());
                	value = disposition.get("filename");
                    value = value.substring(1, value.length() - 1);
                }
                multiput(parms, pname, value);
            }
        } catch (IOException ioe) {
            throw new ResponseException(Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(), ioe);
        }
    }
    
    protected static void multiput(Map<String, String> map, String key, String value) {
    	String existingValue = map.get(key);
    	if (existingValue != null) value = existingValue + NanoHTTPD.HTTPSession.MULTIPLE_VALUE_DELIM + value;
    	map.put(key, value);
    }

    
    public static void main(String[] args) throws Exception {
    	List<FilePart> parts;
    	long INF = Long.MAX_VALUE;
    	NanoHTTPD.TempFileManager tfm = new NanoHTTPD.DefaultTempFileManager();
    	//InputStream sample = new FileInputStream("data/sample-text");
    	//readAndSplitByBoundary(sample, INF, "----WebKitFormBoundaryzGdNWMm8HLWEppfe");

    	//InputStream sample = new FileInputStream("data/sample-png");
    	//readAndSplitByBoundary(sample, INF, "----WebKitFormBoundaryYnxSpIACtmv7gffN");
    	
    	InputStream sample = new FileInputStream("data/sample-mp3");
    	parts = readAndSplitByBoundary(sample, INF, "----WebKitFormBoundaryPsF6n9FIsdrJmABu", tfm);
    	
    	for (FilePart part : parts) {
    		System.out.println(" - " + part.body.getName());
    	}
    	
    	Map<String, String> params = new HashMap<>();
    	Map<String, String> files = new HashMap<>();
    	try {
    		decodeMultipartData(parts, params, files);
    	}
    	catch (ResponseException e) { 
    		System.err.println(e.getMessage());
    	}
    	
    	for (Map.Entry<String, String> entry : params.entrySet()) {
    		System.out.println(entry.getKey() + "=" + entry.getValue());
    	}
    	for (Map.Entry<String, String> entry : files.entrySet()) {
    		System.out.println(entry.getKey() + "=" + entry.getValue());
    	}
	}

}
