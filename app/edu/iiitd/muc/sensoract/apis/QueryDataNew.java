/*******************************************************************************
 * /*
 * * Copyright (c) 2012, Indraprastha Institute of Information Technology,
 * * Delhi (IIIT-D) and The Regents of the University of California.
 * * All rights reserved.
 * *
 * * Redistribution and use in source and binary forms, with or without
 * * modification, are permitted provided that the following conditions
 * * are met:
 * * 1. Redistributions of source code must retain the above copyright
 * * notice, this list of conditions and the following disclaimer.
 * * 2. Redistributions in binary form must reproduce the above
 * * copyright notice, this list of conditions and the following
 * * disclaimer in the documentation and/or other materials provided
 * * with the distribution.
 * * 3. Neither the names of the Indraprastha Institute of Information
 * * Technology, Delhi and the University of California nor the names
 * * of their contributors may be used to endorse or promote products
 * * derived from this software without specific prior written permission.
 * *
 * * THIS SOFTWARE IS PROVIDED BY THE IIIT-D, THE REGENTS, AND CONTRIBUTORS
 * * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE IIITD-D, THE REGENTS
 * * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * * SUCH DAMAGE.
 * *
 * *
 ******************************************************************************/
package edu.iiitd.muc.sensoract.apis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.RectangleInsets;

import play.libs.WS.HttpResponse;
import edu.iiitd.muc.sensoract.constants.Const;
import edu.iiitd.muc.sensoract.format.*;
import edu.iiitd.muc.sensoract.format.QueryDataOutputFormat.Datapoint;

import java.util.*;
import edu.iiitd.muc.sensoract.format.QueryDevice;
import edu.iiitd.muc.sensoract.format.QueryRequest;
import edu.iiitd.muc.sensoract.format.QueryToRepo;
import edu.iiitd.muc.sensoract.format.WaveSegmentArray;
import edu.iiitd.muc.sensoract.utilities.SecretKey;
import edu.iiitd.muc.sensoract.utilities.SendHTTPRequest;
import play.libs.WS;
import play.libs.WS.HttpResponse;
import play.libs.WS.WSRequest;

public class QueryDataNew extends SensorActAPI {

	public final void doProcess(String queryBody) {
		
		System.out.println("......................" + queryBody);

		ArrayList<WaveSegmentArray> arrayOfResponses = new ArrayList<WaveSegmentArray>();
		QueryRequest queryRequest = gson
				.fromJson(queryBody, QueryRequest.class);
		
		//System.out.println(gson.toJson(queryRequest));
		
		String usertype = session.get(Const.USERTYPE);
		String secretkey = null;
		String vpdsURL = null;
		HttpResponse responseFromServer = null;
		
		if(usertype.equals(Const.USER)){
			/* Get accesskey from Broker */			
			// From Json - vpdsname and secretkey
			String usersecretkey = new SecretKey().getSecretKeyFromHashMap(session
					.get(Const.USERNAME));
			String jsonGetAccessKey = "{\"secretkey\" : \"" + usersecretkey + "\",\"vpdsname\": \""+ queryRequest.vpdsname + "\"}";
			logger.info(Const.API_QUERYDATA, "For "+ usertype + " " +jsonGetAccessKey);
			
			// Make request
			responseFromServer = new SendHTTPRequest()
			.sendPostRequest(Const.URL_BROKER_GET_ACCESS_KEY,
					Const.MIME_TYPE_JSON, Const.API_QUERYDATA,
					jsonGetAccessKey);
			System.out.println("Get access key "+responseFromServer.getString());
			GetAccessKeyResponseFormat response = gson.fromJson(
					responseFromServer.getString(),GetAccessKeyResponseFormat.class);
			
			//Set secretkey as accesskey
			secretkey = response.accesskey;
			vpdsURL = response.vpdsurl;
		}
		else if(usertype.equals(Const.OWNER)){
			//Set secretkey as owner key
			secretkey = Global.VPDS_OWNER_KEY;
			vpdsURL = Global.URL_REPOSITORY_SERVER;
		}
		
		if(secretkey == null || vpdsURL == null) {
			renderJSON(gson.toJson(new APIResponse(Const.API_QUERYDATA, 1,
					Const.LOGGER_INFO_SESSION_EXPIRED)));
		}
		
		System.out.println("Fetching data from " + vpdsURL + secretkey);
		List<QueryDataOutputFormat> dataList = fetchDataFromVPDS(vpdsURL, secretkey,queryRequest);
		
		if (dataList == null || dataList.size()==0) {
			renderText("No Data Found");
		}
		processInteractive(dataList);
	}
	
	public List<QueryDataOutputFormat> fetchDataFromVPDS(String vpdsURL, String secretkey, QueryRequest queryRequest) {

		List<QueryDataOutputFormat> dataList = new ArrayList<QueryDataOutputFormat>();
		
		for(QueryDevice query: queryRequest.devicesArray){		
			String device = query.device;
			String sensor = query.sensor;
			String channel = query.channel;

			String url = vpdsURL + device + "/" + sensor + "/" + channel;
			url = url + "?start=" + queryRequest.conditions.fromtime;
			url = url + "&end=" + queryRequest.conditions.totime;
			
			HttpResponse response = null;
			try {
				Map<String,String> header = new HashMap<String,String>();
				header.put("x-apikey", secretkey);
				
				logger.info(Const.API_QUERYDATA, url);
				
				WSRequest wsr = WS.url(url).headers(header).timeout("300s");		
				response = wsr.get();
				
				//logger.info(Const.API_QUERYDATA, response.getString());
				
				QueryDataOutputFormat data = gson.fromJson(response.getString(), QueryDataOutputFormat.class);
				dataList.add(data);
				
			} catch (Exception e) {
				renderJSON(gson.toJson(new APIResponse(Const.API_QUERYDATA, 1,
						Const.ERROR_MESSAGE_CONNECTION_FAILURE)));
			}
		}		
		return dataList;
	}

	public void processNonInteractive(
			ArrayList<WaveSegmentArray> arrayOfResponses) {
		long t1 = new Date().getTime();
		String a = createChart(arrayOfResponses);		
		String re = "{\"filename\":\"" + a + ".png\"}";
		long t2 = new Date().getTime();
		logger.info(Const.API_QUERYDATA, "Time to create non-interactive graph:" + (t2-t1)/1000 + " seconds");
		renderJSON(re);

	}
	
	private void processInteractive(List<QueryDataOutputFormat> dataList) {
		
		ChartSeriesArray ca = new ChartSeriesArray();
		for(QueryDataOutputFormat data : dataList) {
			
			String seriesName = data.device + "__" + data.sensor + "__" + data.channel;
			
			//ca.chartSeries.add(new ChartSeries(seriesName));
			//ca.chartSeriesStats.add(new ChartSeriesStats(seriesName));
			
			ChartSeries cs = new ChartSeries(seriesName);
			ChartSeriesStats css = new ChartSeriesStats(seriesName);
			
			for(Datapoint dp : data.datapoints) {
				double[] d = new double[2];
				d[0] = Double.parseDouble(dp.time);
				d[1] = Double.parseDouble(dp.value);
				cs.data.add(d);
			}
			
			ca.chartSeries.add(cs);
			css.min = 0.0;
			css.max = 0.0;
			css.avg = 0.0;
			ca.chartSeriesStats.add(css);
			//ca.chartSeries.get(j + seriesOffset).data.add(d);
		}
		
		String out = gson.toJson(ca);
		//System.out.println(".........................");
		//System.out.println(out);
		//System.out.println(".........................");
		renderJSON(out);
	}

	private void processInteractiveOld(ArrayList<WaveSegmentArray> arrayOfResponses) {
		long t1 = new Date().getTime();
		int size = arrayOfResponses.size();
		int seriesOffset = 0;
		ChartSeriesArray ca = new ChartSeriesArray();
		logger.info(Const.API_QUERYDATA, "Interactive:: Data Size:" + size);
		for (int a = 0; a < size; a++) {
			//System.out.println("For waveseg " + Integer.toString(a) + " ");
			WaveSegmentArray wa = arrayOfResponses.get(a);

			int numberOfWavesegs = wa.wavesegmentArray.size();
			int numberOfSeries = wa.wavesegmentArray.get(0).data.channels
					.size();

			for (int i = 0; i < numberOfSeries; i++) {
				ca.chartSeries.add(new ChartSeries(
						wa.wavesegmentArray.get(0).data.channels.get(i).cname
								+ " " + wa.wavesegmentArray.get(0).data.sname
								+ " " + wa.wavesegmentArray.get(0).data.dname));

				ca.chartSeriesStats.add(new ChartSeriesStats(
						wa.wavesegmentArray.get(0).data.channels.get(i).cname
								+ " " + wa.wavesegmentArray.get(0).data.sname
								+ " " + wa.wavesegmentArray.get(0).data.dname));

			}

			for (int i = 0; i < numberOfWavesegs; i++) {
				long timestamp = wa.wavesegmentArray.get(i).data.timestamp * 1000;
				int samplingPeriod = 1;
				//System.out.println(timestamp);

				for (int j = 0; j < numberOfSeries; j++) {
					try{

						int numberOfReadings = wa.wavesegmentArray.get(i).data.channels
								.get(j).readings.size();

						Double min = wa.wavesegmentArray.get(i).data.channels
								.get(j).readings.get(0);
						Double max = wa.wavesegmentArray.get(i).data.channels
								.get(j).readings.get(0);
						Double avg = 0.0;

						for (int k = 0; k < numberOfReadings; k++) {
							double[] d = new double[2];
							d[0] = timestamp + k * samplingPeriod * 1000;
							d[1] = wa.wavesegmentArray.get(i).data.channels.get(j).readings
									.get(k);

							ca.chartSeries.get(j + seriesOffset).data.add(d);
							// Min Value
							if (min > d[1])
								min = d[1];

							// Max Value
							if (max < d[1])
								max = d[1];

							// Avg Value
							avg += d[1];
						}

						ca.chartSeriesStats.get(j + seriesOffset).min = min;
						ca.chartSeriesStats.get(j + seriesOffset).max = max;
						ca.chartSeriesStats.get(j + seriesOffset).avg = avg
								/ numberOfReadings;
					}
					catch(NullPointerException e){
						logger.error(Const.API_QUERYDATA + ": NullPointerError: Check packet. Possible error - readings missing");
					}
				}
			}
			seriesOffset += numberOfSeries;

		}
		// System.out.println(gson.toJson(ca));
		long t2 = new Date().getTime();
		logger.info(Const.API_QUERYDATA, "Time to create interactive graph:" + (t2-t1)/1000 +" seconds");
		renderJSON(gson.toJson(ca));

	}

	public String createChart(ArrayList<WaveSegmentArray> arrayOfResponses) {
		logger.info(Const.API_QUERYDATA, "Static Graph:: Data Size:" + arrayOfResponses.size());	
		XYDataset dataset = createDataset(arrayOfResponses);
		JFreeChart chart = createJFreeChart(dataset);
		String uuid = UUID.randomUUID().toString();

		try {
			String path = QueryDataNew.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			String decodedPath = URLDecoder.decode(path, "UTF-8");
			ChartUtilities.saveChartAsPNG(new File(decodedPath+"/"+Const.BASE_IMAGE_URL + uuid
					+ ".png").getCanonicalFile(), chart, 1200, 600);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return uuid;

	}

	public JFreeChart createJFreeChart(XYDataset dataset) {
		//System.out.println(dataset);
		JFreeChart chart = ChartFactory.createTimeSeriesChart("Visualization", // title
				"Date-Time", // x-axis label
				"Readings", // y-axis label
				dataset, // data
				true, // create legend?
				true, // generate tooltips?
				false // generate URLs?
				);

		chart.setBackgroundPaint(Color.white);

		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaint(Color.white);
		plot.setDomainGridlinePaint(Color.black);
		plot.setRangeGridlinePaint(Color.black);
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		plot.setDomainCrosshairVisible(true);
		plot.setRangeCrosshairVisible(true);

		XYItemRenderer r = plot.getRenderer();
		if (r instanceof XYLineAndShapeRenderer) {
			XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
			//renderer.setSeriesStroke(0, new BasicStroke(1.1f));
			//plot.setRenderer(renderer);
		}

		DateAxis axis = (DateAxis) plot.getDomainAxis();
		axis.setDateFormatOverride(new SimpleDateFormat("d-M-yy H:m:s"));
		
		return chart;
	}

	public XYDataset createDataset(ArrayList<WaveSegmentArray> arrayOfResponses) {
		TimeSeriesCollection dataset = new TimeSeriesCollection();
		int numberOfResponses = arrayOfResponses.size();
		if (numberOfResponses>0)
		for (int i = 0; i < numberOfResponses; i++) {
			WaveSegmentArray wa = arrayOfResponses.get(i);
			int numberOfSeries = wa.wavesegmentArray.get(0).data.channels
					.size();
			int numberOfWavesegs = wa.wavesegmentArray.size();
			TimeSeries s1[] = new TimeSeries[numberOfSeries];
			for (int j = 0; j < numberOfSeries; j++) {
				s1[j] = new TimeSeries(
						wa.wavesegmentArray.get(0).data.channels.get(j).cname
								+ " " + wa.wavesegmentArray.get(0).data.sname
								+ " " + wa.wavesegmentArray.get(0).data.dname,
						Millisecond.class);

			}

			for (int a = 0; a < numberOfWavesegs; a++) {

				long timestamp = wa.wavesegmentArray.get(a).data.timestamp * 1000;
				int samplingPeriod = 1;

				for (int j = 0; j < numberOfSeries; j++) {
					
					try{

						int numberOfReadings = wa.wavesegmentArray.get(a).data.channels
								.get(j).readings.size();

						for (int k = 0; k < numberOfReadings; k++) {

							Millisecond x = new Millisecond(new Date(
									new Double((timestamp + k * samplingPeriod
											* 1000)).longValue()));

							double y = wa.wavesegmentArray.get(a).data.channels
									.get(j).readings.get(k);

							s1[j].addOrUpdate(x, y);


						}
					}
					catch(NullPointerException e){
						logger.error(Const.API_QUERYDATA + ": Null Pointer: Check packet. Possible error - readings missing");
					}

				}

			}
			for (int j = 0; j < numberOfSeries; j++) {
				dataset.addSeries(s1[j]);
			}

		}

		// TODO Auto-generated method stub
		return dataset;
	}
}
