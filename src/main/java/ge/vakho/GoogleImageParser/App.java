package ge.vakho.GoogleImageParser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @author vakho
 *
 */
public class App {

	public static void main(String[] args) {

		App obj = new App();
		Set<String> result = obj.getImgUrlsFromGoogle();
		for (String temp : result) {
			System.out.println(temp);
		}
		System.out.println(result.size());
	}

	private Set<String> getImgUrlsFromGoogle() {

		Set<String> searchStringSet = new TreeSet<>();
		searchStringSet.add("spider woman");
		searchStringSet.add("spiderwoman");
		Set<String> result = new HashSet<String>();
		String request = "https://www.google.com/search?tbm=isch&num=100&as_oq=" + makeSearchStringFromSet(searchStringSet);
		System.out.println("Sending request..." + request);

		try {

			// need http protocol, set this as a Google bot agent :)
			Document doc = Jsoup.connect(request).userAgent("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)").timeout(1000 * 20).get();

			Element body = doc.body();
			System.out.println(body.toString());
			// get all links
			String pattern = "https://www.google.com/imgres";
			Elements links = doc.select("a[href]");
			for (Element link : links) {
				String temp = link.absUrl("href");
				if (temp.contains("pattern")) {
					result.add(temp);					
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	public static String makeSearchStringFromSet(Set<String> searchStringSet) {
		StringBuilder strBuilder = new StringBuilder();
		for (Iterator iterator = searchStringSet.iterator(); iterator.hasNext();) {
			String string = (String) iterator.next();
			String encoded;
			strBuilder.append("\"");
			strBuilder.append(string);
			if (iterator.hasNext()) {
				strBuilder.append("\"+");
			} else {
				strBuilder.append("\"");
			}
			try {
				encoded = URLEncoder.encode(string, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				continue;
			}
		}
		return strBuilder.toString();
	}
}
