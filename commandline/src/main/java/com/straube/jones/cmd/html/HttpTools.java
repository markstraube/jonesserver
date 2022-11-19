package com.straube.jones.cmd.html;


import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class HttpTools
{
	public final static String HTML_NOT_FOUND = "<!DOCTYPE html><html lang=\"de\"><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no\"/><meta charset=\"utf-8\" /></head><body class=\"body\">not found</body></html>";

	public static String downloadFromWebToFile(String url, File htmlFile, boolean bOverwrite)
		throws Exception
	{
		if (!bOverwrite && htmlFile.exists())
		{ return FileUtils.readFileToString(htmlFile, "UTF-8"); }
		// Thread.sleep(100);

		try (CloseableHttpClient httpclient = HttpClients.createDefault())
		{
			final HttpGet httpget = new HttpGet(url);

			final ResponseHandler<String> responseHandler = response -> {
				final int status = response.getStatusLine().getStatusCode();
				final HttpEntity entity = response.getEntity();
				return entity != null ? EntityUtils.toString(entity) : HTML_NOT_FOUND;
			};
			final String htmlString = httpclient.execute(httpget, responseHandler);
			if (htmlString != null)
			{
				FileUtils.writeStringToFile(htmlFile, htmlString, "UTF-8");
				return htmlString;
			}
		}
		return null;
	}


	public static boolean downloadBinaryDataFromWebToFile(String url, File binaryFile, boolean bOverwrite)
		throws Exception
	{
		if (!bOverwrite && binaryFile.exists())
		{ return true; }
		Thread.sleep(100);

		try (final CloseableHttpClient httpclient = HttpClients.createDefault())
		{
			final HttpGet httpget = new HttpGet(url);

			final ResponseHandler<byte[]> responseHandler = response -> {
				final int status = response.getStatusLine().getStatusCode();
				final HttpEntity entity = response.getEntity();
				return entity != null ? EntityUtils.toByteArray(entity) : HTML_NOT_FOUND.getBytes();
			};
			final byte[] imgBytes = httpclient.execute(httpget, responseHandler);
			if (imgBytes != null && imgBytes.length > 0)
			{
				try (final FileOutputStream fout = new FileOutputStream(binaryFile))
				{
					fout.write(imgBytes);
					return true;
				}
			}
			return false;
		}
	}


	public static String downloadFromWebToString(String url)
		throws Exception
	{
		try (final CloseableHttpClient httpclient = HttpClients.createDefault())
		{
			URLEncoder.encode(url, Charset.forName("UTF-8"));
			final HttpGet httpget = new HttpGet(url);

			final ResponseHandler<String> responseHandler = response -> {
				final int status = response.getStatusLine().getStatusCode();
				final HttpEntity entity = response.getEntity();
				return entity != null ? EntityUtils.toString(entity) : HTML_NOT_FOUND;
			};
			final String htmlString = httpclient.execute(httpget, responseHandler);

			return htmlString;
		}
	}


	public static String postUrl(String url, File htmlFile, List<NameValuePair> formData, boolean bOverwrite)
		throws Exception
	{
		if (htmlFile != null && (!bOverwrite && htmlFile.exists()))
		{ return new String(Files.readAllBytes(Paths.get(htmlFile.toURI()))); }

		try (final CloseableHttpClient httpclient = HttpClients.createDefault())
		{
			final HttpPost httppost = new HttpPost(url);

			httppost.setEntity(new UrlEncodedFormEntity(formData));
			httppost.setHeader("Accept", "*/*");
			httppost.setHeader("Content-type", "application/x-www-form-urlencoded; charset=UTF-8");

			final ResponseHandler<String> responseHandler = response -> {
				final int status = response.getStatusLine().getStatusCode();
				final HttpEntity entity = response.getEntity();
				return entity != null ? EntityUtils.toString(entity) : HTML_NOT_FOUND;
			};
			final String htmlString = httpclient.execute(httppost, responseHandler);
			if (htmlString != null && htmlFile != null)
			{
				Files.writeString(Paths.get(htmlFile.toURI()), htmlString, Charset.forName("UTF-8"), StandardOpenOption.CREATE);
			}
			return htmlString;
		}
	}


	public static void main(String[] args)
		throws Exception
	{}
}
