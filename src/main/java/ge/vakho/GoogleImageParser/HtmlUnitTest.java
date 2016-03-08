package ge.vakho.GoogleImageParser;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.common.base.Predicate;

public class HtmlUnitTest {

	private static void delayBy(long seconds) {
		try {
			Thread.sleep(1000 * seconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void certStoreValidationOff() {
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}
		} };

		// Install the all-trusting trust manager
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (GeneralSecurityException e) {
		}
	}

	private static WebElement getShowMoreButton(WebDriver driver) {
		return driver.findElement(By.cssSelector("input[type='button'][value='Show more results']"));
	}

	private static void scrollToBottom(WebDriver driver) {
		JavascriptExecutor jse = (JavascriptExecutor) driver;
		jse.executeScript("window.scrollBy(0, document.body.scrollHeight)");

		delayBy(1);
	}

	private static void waitForWebsiteToRender(WebDriver driver) {
		// If exception is thrown, it will wait only one second!
		WebDriverWait wait = new WebDriverWait(driver, 1);

		System.out.println("Waiting for ready state...");
		wait.until(new Predicate<WebDriver>() {
			public boolean apply(WebDriver driver) {
				return ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete");
			}
		});
		System.out.println("Document is ready!");
	}

	public static void main(String[] args) {
		certStoreValidationOff();

		String tsuPdfFiles = "https://www.google.com/search?q=tsu.ge&as_filetype=pdf&as_sitesearch=tsu.ge&as_qdr=y&num=100";
		
		WebDriver driver = new FirefoxDriver();
		driver.navigate().to("http://www.google.com/search?tbm=isch&as_oq=\"donals trump\"");

		waitForWebsiteToRender(driver);

		int attempts = 0;
		while (true) {
			System.out.println("Attempt:" + attempts);
			if (attempts >= 5) {
				break;
			}
			try {
				WebElement btnShowMoreResult = getShowMoreButton(driver);
				if (!btnShowMoreResult.isDisplayed()) {
					scrollToBottom(driver);
					attempts++;
				} else {
					btnShowMoreResult.click();
					attempts = 0;
					delayBy(1);
				}
			} catch (NoSuchElementException e) {
				e.printStackTrace();
				break;
			}
		}

		String pageSource = driver.getPageSource();		
		driver.quit();
		
		Document doc = Jsoup.parse(pageSource);

		// Image links
		Set<String> results = new HashSet<String>();

		Elements links = doc.select("a[href]");
		for (Element link : links) {
			String temp = link.absUrl("href");
			// System.out.println(temp);
			if (temp.contains("imgres")) {
				String rs = temp.substring(temp.lastIndexOf("imgurl=") + "imgurl=".length(), temp.indexOf("&"));
				int lastIndex = rs.lastIndexOf(".jpg");
				if (lastIndex == -1) {
					lastIndex = rs.lastIndexOf(".png");
				}
				rs = rs.substring(0, lastIndex + 4);
				results.add(rs);
			}
		}

		System.out.println("Results: " + results.size());

		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
		String format = sdf.format(cal.getTime());

		String path = "imgs/" + format;
		File dir = new File(path);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		path += "/";
		
		int counter = 1;
		for (String res : results) {
			String name = res.substring(res.lastIndexOf("/") + 1, res.length());

			HttpClient client = HttpClients.custom().setUserAgent("Mozilla/5.0").build();
			HttpGet request = new HttpGet(res);

			InputStream inputContentStream = null;
			ByteArrayOutputStream baos = null;
			InputStream streamForChecking = null;
			InputStream downloadStream = null;
			OutputStream outputStream = null;
			try {
				HttpResponse response = client.execute(request);
				inputContentStream = response.getEntity().getContent();

				// Copy contents into byte array to user twice.
				baos = new ByteArrayOutputStream();
				IOUtils.copy(inputContentStream, baos);
				byte[] bytes = baos.toByteArray();

				streamForChecking = new ByteArrayInputStream(bytes);
				Image image = ImageIO.read(streamForChecking);
				if (image == null) {
					System.out.println("Invalid Image!");
					continue;
				}
				downloadStream = new ByteArrayInputStream(bytes);
				System.out.println(counter + ".\t"  + res);

				outputStream = new FileOutputStream(path + name);
				IOUtils.copy(downloadStream, outputStream);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				closeStream(inputContentStream);
				closeStream(baos);
				closeStream(streamForChecking);
				closeStream(downloadStream);
				closeStream(outputStream);
				
				++counter;
			}
		}
	}

	/**
	 * Function closes any kind of stream (input or output) accodingly.
	 * 
	 * @param stream
	 */
	private static void closeStream(Object stream) {

		if (stream instanceof InputStream) {
			InputStream input = (InputStream) stream;
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
				}
			}
		} else if (stream instanceof InputStream) {
			OutputStream output = (OutputStream) stream;
			if (output != null) {
				try {
					output.close();
				} catch (IOException e) {
				}
			}
		}
	}
	
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
}
