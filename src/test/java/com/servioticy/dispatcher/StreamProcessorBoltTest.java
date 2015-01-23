/*******************************************************************************
* Copyright 2014 Barcelona Supercomputing Center (BSC)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
******************************************************************************/

package com.servioticy.dispatcher;

import backtype.storm.task.OutputCollector;
import backtype.storm.tuple.Tuple;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servioticy.datamodel.UpdateDescriptor;
import com.servioticy.datamodel.sensorupdate.SUChannel;
import com.servioticy.datamodel.sensorupdate.SensorUpdate;
import com.servioticy.datamodel.serviceobject.SO;
import com.servioticy.datamodel.serviceobject.SO020;
import com.servioticy.dispatcher.bolts.StreamProcessorBolt;
import com.servioticy.queueclient.QueueClient;
import com.servioticy.restclient.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * @author Álvaro Villalba Navarro <alvaro.villalba@bsc.es>
 *
 */
public class StreamProcessorBoltTest {

    private static String SO_BASIC;
    private static String SU_A;
    private static ObjectMapper mapper;

    private static String readFile(String path) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        byte[] encoded = Files.readAllBytes(Paths.get(cl.getResource(path).getPath()));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    static private FutureRestResponse mockFutureRestResponse(int resCode, String resBody)
            throws InterruptedException, ExecutionException, RestClientException, RestClientErrorCodeException {
        RestResponse rr = new RestResponse(resBody, resCode);
        FutureRestResponse futureRestResponse = mock(FutureRestResponse.class, withSettings().serializable());
        when(futureRestResponse.get()).thenReturn(rr);
        return futureRestResponse;
//        return new FutureRestResponse(new RestResponse(resBody, resCode)) {
//                                      @Override
//                                      public RestResponse get() {
//                                          return this.restResponse;
//                                      }
//                                  };
    }

    private void mockEmptyGetStreamSU(DispatcherContext dc, RestClient restClient, String soId, String streamId) throws Exception {
        when(restClient.restRequest(
                dc.restBaseURL
                        + "private/" + soId + "/streams/" + streamId + "/lastUpdate",
                null, RestClient.GET,
                null)).thenReturn(mockFutureRestResponse(204, null));
    }
    private void mockCorrectGetGroupSU(DispatcherContext dc, RestClient restClient, String group, String suStr) throws Exception {
        when(restClient.restRequest(
                dc.restBaseURL
                        + "private/groups/lastUpdate", mapper.writeValueAsString(group),
                RestClient.POST,
                null)).thenReturn(mockFutureRestResponse(200, suStr));
    }

    @BeforeClass
    public static void init() throws IOException {
        mapper = new ObjectMapper();

        SO_BASIC = readFile("0.2.0/so-basic.json");
        SU_A = readFile("0.2.0/su-A.json");
    }

    @Test
    public void testExecute() throws IOException, RestClientException, RestClientErrorCodeException, ExecutionException, InterruptedException {
        final OutputCollector collector = mock(OutputCollector.class, withSettings().serializable());
        final Tuple tuple = mock(Tuple.class, withSettings().serializable());
        final DispatcherContext dc = mock(DispatcherContext.class, withSettings().serializable());
        final RestClient restClient = mock(RestClient.class, withSettings().serializable());
        final QueueClient qc = mock(QueueClient.class, withSettings().serializable());
        final SO so = mapper.readValue(SO_BASIC, SO.class);
        final SensorUpdate suB = mapper.readValue(SU_A, SensorUpdate.class);
        suB.setLastUpdate(2);

        when(tuple.getStringByField("soid")).thenReturn(so.getId());
        when(tuple.getStringByField("streamid")).thenReturn("B");
        when(tuple.getStringByField("su")).thenReturn(mapper.writeValueAsString(suB));
        when(tuple.getStringByField("so")).thenReturn(SO_BASIC);
        when(tuple.getStringByField("originid")).thenReturn("A");

        dc.restBaseURL = "localhost/";

        FutureRestResponse futureRestResponse = mockFutureRestResponse(204, null);
        when(restClient.restRequest(
                dc.restBaseURL
                        + "private/" + so.getId() + "/streams/B/lastUpdate",
                null, RestClient.GET,
                null)).thenReturn(futureRestResponse);

        futureRestResponse = mockFutureRestResponse(200, SU_A);
        when(restClient.restRequest(
                dc.restBaseURL
                        + "private/groups/lastUpdate", mapper.writeValueAsString(so.getGroups().get("group")),
                RestClient.POST,
                null)).thenReturn(futureRestResponse);

        when(qc.isConnected()).thenReturn(true);
        when(qc.put(anyObject())).then(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                UpdateDescriptor ud = mapper.readValue((String) args[0], UpdateDescriptor.class);

                Assert.assertTrue("Operation id", ud.getOpid() != null);
                Assert.assertTrue("Origin SO id", ud.getSoid().equals(so.getId()));
                Assert.assertTrue("Origin stream id", ud.getStreamid().equals("B"));
                Assert.assertTrue("New SU timestamp", ud.getSu().getLastUpdate() == suB.getLastUpdate());
                SUChannel suCh = ud.getSu().getChannels().get("$");
                double cValue = (Double) suCh.getCurrentValue();
                Assert.assertTrue("New SU current-value", cValue == 2.0);

                return true;
            }
        });

        StreamProcessorBolt bolt = new StreamProcessorBolt(dc, qc, restClient);
        bolt.prepare(null, null, collector);
        bolt.execute(tuple);
        Mockito.verify(qc).put(anyObject());

    }
}
