package com.ericsson.gerrit.plugins.eiffel.events.generators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.gerrit.plugins.eiffel.events.EiffelSourceChangeCreatedEvent;
import com.ericsson.gerrit.plugins.eiffel.events.EiffelSourceChangeSubmittedEvent;
import com.ericsson.gerrit.plugins.eiffel.exceptions.NoSuchElementException;
import com.ericsson.gerrit.plugins.eiffel.git.CommitInformation;
import com.ericsson.gerrit.plugins.eiffel.storage.EventStorageFactory;
import com.ericsson.gerrit.plugins.eiffel.storage.SourceChangeCreatedStorage;
import com.ericsson.gerrit.plugins.eiffel.storage.SourceChangeSubmittedStorage;
import com.google.common.base.Supplier;
import com.google.gerrit.reviewdb.client.Change.Key;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.events.ChangeMergedEvent;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ EiffelEventGenerator.class, InetAddress.class, EventStorageFactory.class })
public class EiffelEventGeneratorTest {

    private static final String PARENT_SHA = "parent-sha";

    private static final Logger LOGGER = LoggerFactory.getLogger(EiffelEventGeneratorTest.class);

    private static final String SCS_EVENT = "EiffelSourceChangeSubmittedEvent";
    private static final String SCC_EVENT = "EiffelSourceChangeCreatedEvent";
    private static final String COMMIT_ID = "00000000-0000-0000-0000-000000000000";
    private static final String CHANGE_ID = "I13400c37d648c2eedd9eaa24c136bc6d98e9a791";
    private static final String SOURCE_NAME = "Eiffel Gerrit Plugin";
    private static final String PROJECT = "my-project";
    private static final String BRANCH = "my-branch";
    private static final String URL = "http://my-url.com";
    private static final String NULL_HOST = "no-host/";
    private static final String NAME = "User Usersson";
    private static final String USERNAME = "my-user";
    private static final String EMAIL = "my@email.com";
    private static final int SIZE_INSERTIONS = 1;
    private static final int SIZE_DELETIONS = 1;
    private static final int DEFAULT_PORT = 29418;

    private Gson gson = new Gson();

    private PatchSetCreatedEvent patchSetCreatedEvent;
    private ChangeMergedEvent changeMergedEvent;
    private Supplier<ChangeAttribute> supplierChangeAttribute;
    private Supplier<PatchSetAttribute> supplierPatchSetAttribute;
    private ChangeAttribute changeAttribute;
    private PatchSetAttribute patchSetAttribute;
    private AccountAttribute accountAttribute;
    private Key changeKey;

    private CommitInformation commitInformation;

    private File pluginDirectory;

    @Captor
    private ArgumentCaptor<String> keyCaptor;
    @Captor
    private ArgumentCaptor<String> valueCaptor;

    @Before
    public void setUp() throws ConnectException, FileNotFoundException, NoSuchElementException {
        setUpMocks();
    }

    @Test
    public void testEiffelSourceChangeSubmittedEventGenerator() {
        populateChangeMergedEvent();

        EiffelSourceChangeSubmittedEvent eiffelEvent =
                EiffelSourceChangeSubmittedEventGenerator.generate(
                        changeMergedEvent, pluginDirectory, commitInformation);

        verifyEiffelSourceChangeSubmittedEvent(eiffelEvent);
    }

     @Test
     public void testEiffelSourceChangeCreatedEventGenerator() {
     populatePatchSetCreatedEvent();

        EiffelSourceChangeCreatedEvent eiffelEvent = EiffelSourceChangeCreatedEventGenerator.generate(
                patchSetCreatedEvent, pluginDirectory, commitInformation);

     verifyEiffelSourceChangeCreatedEvent(eiffelEvent);
     }

    @Test
    public void testEiffelEventGeneratorHostNameException() {
        setUpHostNameExceptionMock();

        String hostName = EiffelEventGenerator.determineHostName();
        assertEquals("Host name should have been set to null", null, hostName);
    }

    @Test
    public void testEiffelEventGeneratorRepoUriException() {
        setUpRepoUriExceptionMocks();

        String repoURI = EiffelEventGenerator.createRepoURI(URL, PROJECT);
        assertEquals("Repo URI should have been set to null", null, repoURI);
    }

    @Test
    public void testEiffelEventGeneratorSshBaseUrlException() {
        setUpSshBaseUrlExceptionMocks();

        String repoURI = EiffelEventGenerator.createRepoURI(URL, PROJECT);
        assertEquals("Repo URI should have been set to null", null, repoURI);
    }

    @Test
    public void testEiffelEventGeneratorNullHost() {
        String repoURI = EiffelEventGenerator.createRepoURI(NULL_HOST, PROJECT);

        assertNull("Repo URI should have been set to null", repoURI);
    }

    @Test
    public void testGetPreviousEiffelEventInSourceChangeCreated()
            throws ConnectException, FileNotFoundException, NoSuchElementException {
        populatePatchSetCreatedEvent();

        SourceChangeCreatedStorage sourceChangeCreatedState = mock(SourceChangeCreatedStorage.class);
        when(EventStorageFactory.getEventStorage(Mockito.any(), Mockito.any())).thenReturn(sourceChangeCreatedState);

        EiffelSourceChangeCreatedEventGenerator.generate(patchSetCreatedEvent, pluginDirectory,
                commitInformation);

        Mockito.verify(sourceChangeCreatedState, Mockito.times(2)).getEventId(keyCaptor.capture(),
                valueCaptor.capture());

        List<String> parametersCalledWith = valueCaptor.getAllValues();
        assertEquals("Incorrect parameter, should have been a changeID", CHANGE_ID, parametersCalledWith.get(0));
        assertEquals("Incorrect parameter, should have been a branch.", PARENT_SHA, parametersCalledWith.get(1));
    }

    @Test
    public void testGetPreviousEiffelEventInSourceChangeSubmitted()
            throws ConnectException, FileNotFoundException, NoSuchElementException {
        populateChangeMergedEvent();

        SourceChangeSubmittedStorage sourceChangeSubmittedState = mock(SourceChangeSubmittedStorage.class);
        when(EventStorageFactory.getEventStorage(Mockito.any(), Mockito.any())).thenReturn(sourceChangeSubmittedState);

        EiffelSourceChangeSubmittedEventGenerator.generate(changeMergedEvent, pluginDirectory,
                commitInformation);

        Mockito.verify(sourceChangeSubmittedState, Mockito.times(2)).getEventId(keyCaptor.capture(),
                valueCaptor.capture());

        List<String> parametersCalledWith = valueCaptor.getAllValues();
        assertEquals("Incorrect parameter, should have been a changeID", CHANGE_ID, parametersCalledWith.get(0));
        assertEquals("Incorrect parameter, should have been a branch.", PARENT_SHA, parametersCalledWith.get(1));
    }

    @Test
    public void testCreateLinksForSCCWithNoPreviousEventIdSaved()
            throws ConnectException, FileNotFoundException, NoSuchElementException {
        populatePatchSetCreatedEvent();

        SourceChangeCreatedStorage sourceChangeCreatedState = mock(SourceChangeCreatedStorage.class);
        when(EventStorageFactory.getEventStorage(Mockito.any(), Mockito.any())).thenReturn(sourceChangeCreatedState);
        when(sourceChangeCreatedState.getEventId(Mockito.any(), Mockito.any()))
                .thenThrow(new NoSuchElementException("exception"));

        EiffelSourceChangeCreatedEvent eiffelEvent = EiffelSourceChangeCreatedEventGenerator
                .generate(patchSetCreatedEvent, pluginDirectory, commitInformation);
        assertEquals("No links should have been created.", 0, eiffelEvent.eventParams.links.size());
    }

    @Test
    public void testCreateLinksForSCSWithNoPreviousEventIdSaved()
            throws ConnectException, FileNotFoundException, NoSuchElementException {
        populateChangeMergedEvent();

        SourceChangeSubmittedStorage sourceChangeSubmittedState = mock(SourceChangeSubmittedStorage.class);
        when(EventStorageFactory.getEventStorage(Mockito.any(), Mockito.any())).thenReturn(sourceChangeSubmittedState);
        when(sourceChangeSubmittedState.getEventId(Mockito.any(), Mockito.any()))
                .thenThrow(new NoSuchElementException("exception"));

        EiffelSourceChangeSubmittedEvent eiffelEvent = EiffelSourceChangeSubmittedEventGenerator
                .generate(changeMergedEvent, pluginDirectory, commitInformation);
        assertEquals("No links should have been created.", 0, eiffelEvent.eventParams.links.size());
    }

    @Test
    public void testCreateLinksForSCCWithPreviousEventIdSaved()
            throws ConnectException, FileNotFoundException, NoSuchElementException {
        populatePatchSetCreatedEvent();

        SourceChangeCreatedStorage sourceChangeCreatedState = mock(SourceChangeCreatedStorage.class);
        when(EventStorageFactory.getEventStorage(Mockito.any(), Mockito.any())).thenReturn(sourceChangeCreatedState);
        when(sourceChangeCreatedState.getEventId(Mockito.any(), Mockito.any())).thenReturn("previous-event-id");

        EiffelSourceChangeCreatedEvent eiffelEvent = EiffelSourceChangeCreatedEventGenerator
                .generate(patchSetCreatedEvent, pluginDirectory, commitInformation);

        String expectedTypePreviousVersion = "PREVIOUS_VERSION";
        String expectedTypeBase = "BASE";

        String message = "Incorrect link type.";
        assertEquals(message, expectedTypePreviousVersion, eiffelEvent.eventParams.links.get(0).type);
        assertEquals(message, expectedTypeBase, eiffelEvent.eventParams.links.get(1).type);
    }

    @Test
    public void testCreateLinksForSCSWithPreviousEventIdSaved()
            throws ConnectException, FileNotFoundException, NoSuchElementException {
        populateChangeMergedEvent();

        SourceChangeSubmittedStorage sourceChangeSubmittedState = mock(SourceChangeSubmittedStorage.class);
        when(EventStorageFactory.getEventStorage(Mockito.any(), Mockito.any())).thenReturn(sourceChangeSubmittedState);
        when(sourceChangeSubmittedState.getEventId(Mockito.any(), Mockito.any())).thenReturn("previous-event-id");

        EiffelSourceChangeSubmittedEvent eiffelEvent = EiffelSourceChangeSubmittedEventGenerator
                .generate(changeMergedEvent, pluginDirectory, commitInformation);

        String expectedTypeChange = "CHANGE";
        String expectedTypePreviousVersion = "PREVIOUS_VERSION";

        String message = "Incorrect link type.";
        assertEquals(message, expectedTypeChange, eiffelEvent.eventParams.links.get(0).type);
        assertEquals(message, expectedTypePreviousVersion, eiffelEvent.eventParams.links.get(1).type);
    }

    @SuppressWarnings("unchecked")
    private void setUpMocks() throws ConnectException, FileNotFoundException, NoSuchElementException {
        changeMergedEvent = mock(ChangeMergedEvent.class);
        patchSetCreatedEvent = mock(PatchSetCreatedEvent.class);
        supplierChangeAttribute = mock(Supplier.class);
        changeAttribute = mock(ChangeAttribute.class);
        supplierPatchSetAttribute = mock(Supplier.class);
        patchSetAttribute = mock(PatchSetAttribute.class);
        accountAttribute = mock(AccountAttribute.class);
        changeKey = mock(Key.class);
        changeMergedEvent.changeKey = changeKey;
        pluginDirectory = mock(File.class);
        commitInformation = mock(CommitInformation.class);

        mockStatic(EventStorageFactory.class);
        SourceChangeSubmittedStorage sourceChangeSubmittedState = mock(SourceChangeSubmittedStorage.class);

        when(supplierChangeAttribute.get()).thenReturn(changeAttribute);
        when(supplierPatchSetAttribute.get()).thenReturn(patchSetAttribute);
        when(changeKey.toString()).thenReturn(CHANGE_ID);

        when(EventStorageFactory.getEventStorage(Mockito.any(), Mockito.any())).thenReturn(sourceChangeSubmittedState);
        when(sourceChangeSubmittedState.getEventId(Mockito.any(), Mockito.any())).thenReturn("my_event_id");

        when(commitInformation.getParentsSHAs(COMMIT_ID, PROJECT)).thenReturn(Arrays.asList(PARENT_SHA));

    }

    private void setUpHostNameExceptionMock() {
        mockStatic(InetAddress.class);
        try {
            when(InetAddress.getLocalHost()).thenThrow(UnknownHostException.class);
        } catch (Exception ignore) {
        }
    }

    private void setUpRepoUriExceptionMocks() {
        try {
            whenNew(URI.class).withArguments(URL).thenThrow(URISyntaxException.class);
        } catch (Exception ignore) {
        }
    }

    private void setUpSshBaseUrlExceptionMocks() {
        try {
            URI uri = new URI(URL);
            String host = uri.getHost();
            whenNew(URI.class).withArguments(URL).thenReturn(uri);
            whenNew(URI.class).withArguments("ssh", null, host, DEFAULT_PORT, "/", null, null)
                              .thenThrow(URISyntaxException.class);
        } catch (Exception ignore) {
        }
    }

    private void populateChangeMergedEvent() {
        changeMergedEvent.newRev = COMMIT_ID;
        changeMergedEvent.change = supplierChangeAttribute;
        changeAttribute.project = PROJECT;
        changeAttribute.branch = BRANCH;
        changeAttribute.url = URL;
        changeMergedEvent.patchSet = supplierPatchSetAttribute;
        patchSetAttribute.author = accountAttribute;
        accountAttribute.name = NAME;
        accountAttribute.username = USERNAME;
        accountAttribute.email = EMAIL;
    }

    private void populatePatchSetCreatedEvent() {
        patchSetCreatedEvent.change = supplierChangeAttribute;
        patchSetCreatedEvent.changeKey = changeKey;
        changeAttribute.project = PROJECT;
        changeAttribute.branch = BRANCH;
        changeAttribute.url = URL;
        patchSetCreatedEvent.patchSet = supplierPatchSetAttribute;
        patchSetAttribute.revision = COMMIT_ID;
        patchSetAttribute.author = accountAttribute;
        patchSetAttribute.sizeInsertions = SIZE_INSERTIONS;
        patchSetAttribute.sizeDeletions = SIZE_DELETIONS;
        accountAttribute.name = NAME;
        accountAttribute.username = USERNAME;
        accountAttribute.email = EMAIL;
    }

    private void verifyEiffelSourceChangeSubmittedEvent(
            EiffelSourceChangeSubmittedEvent eiffelEvent) {
        JsonObject eiffelEventJson = gson.toJsonTree(eiffelEvent).getAsJsonObject();
        LOGGER.debug("EiffelSourceChangeSubmittedEvent : \n{}", eiffelEventJson.toString());

        JsonObject meta = eiffelEventJson.getAsJsonObject("msgParams").getAsJsonObject("meta");
        JsonObject source = meta.getAsJsonObject("source");
        JsonObject data = eiffelEventJson.getAsJsonObject("eventParams").getAsJsonObject("data");
        JsonObject submitter = data.getAsJsonObject("submitter");
        JsonObject gitIdentifier = data.getAsJsonObject("gitIdentifier");
        JsonObject eventParams = eiffelEventJson.getAsJsonObject("eventParams");

        JsonArray links = eventParams.getAsJsonArray("links");
        JsonObject link1 = links.get(0).getAsJsonObject();
        JsonObject link2 = links.get(1).getAsJsonObject();

        String errorMessage = "Eiffel event did not generate properly";
        assertEquals(errorMessage, SCS_EVENT, meta.get("type").getAsString());
        assertEquals(errorMessage, SOURCE_NAME, source.get("name").getAsString());
        assertEquals(errorMessage, URL, source.get("uri").getAsString());
        assertEquals(errorMessage, NAME, submitter.get("name").getAsString());
        assertEquals(errorMessage, USERNAME, submitter.get("id").getAsString());
        assertEquals(errorMessage, EMAIL, submitter.get("email").getAsString());
        assertEquals(errorMessage, COMMIT_ID, gitIdentifier.get("commitId").getAsString());
        assertEquals(errorMessage, BRANCH, gitIdentifier.get("branch").getAsString());
        assertEquals(errorMessage, PROJECT, gitIdentifier.get("repoName").getAsString());
        assertEquals(errorMessage, "CHANGE", link1.get("type").getAsString());
        assertEquals(errorMessage, "PREVIOUS_VERSION", link2.get("type").getAsString());
    }


    private void verifyEiffelSourceChangeCreatedEvent(
            EiffelSourceChangeCreatedEvent eiffelEvent) {
        JsonObject eiffelEventJson = gson.toJsonTree(eiffelEvent).getAsJsonObject();
        LOGGER.debug("EiffelSourceChangeCreatedEvent : \n{}", eiffelEventJson.toString());

        JsonObject meta = eiffelEventJson.getAsJsonObject("msgParams").getAsJsonObject("meta");
        JsonObject source = meta.getAsJsonObject("source");
        JsonObject data = eiffelEventJson.getAsJsonObject("eventParams").getAsJsonObject("data");
        JsonObject author = data.getAsJsonObject("author");
        JsonObject gitIdentifier = data.getAsJsonObject("gitIdentifier");
        JsonObject change = data.getAsJsonObject("change");
        JsonObject eventParams = eiffelEventJson.getAsJsonObject("eventParams");

        JsonArray links = eventParams.getAsJsonArray("links");
        JsonObject link1 = links.get(0).getAsJsonObject();
        JsonObject link2 = links.get(1).getAsJsonObject();

        String errorMessage = "Eiffel event did not generate properly";
        assertEquals(errorMessage, SCC_EVENT, meta.get("type").getAsString());
        assertEquals(errorMessage, SOURCE_NAME, source.get("name").getAsString());
        assertEquals(errorMessage, URL, source.get("uri").getAsString());
        assertEquals(errorMessage, NAME, author.get("name").getAsString());
        assertEquals(errorMessage, USERNAME, author.get("id").getAsString());
        assertEquals(errorMessage, EMAIL, author.get("email").getAsString());
        assertEquals(errorMessage, COMMIT_ID, gitIdentifier.get("commitId").getAsString());
        assertEquals(errorMessage, BRANCH, gitIdentifier.get("branch").getAsString());
        assertEquals(errorMessage, PROJECT, gitIdentifier.get("repoName").getAsString());
        assertEquals(errorMessage, URL, change.get("details").getAsString());
        assertEquals(errorMessage, CHANGE_ID, change.get("id").getAsString());
        assertEquals(errorMessage, SIZE_INSERTIONS, change.get("insertions").getAsInt());
        assertEquals(errorMessage, SIZE_DELETIONS, change.get("deletions").getAsInt());
        assertEquals(errorMessage, "PREVIOUS_VERSION", link1.get("type").getAsString());
        assertEquals(errorMessage, "BASE", link2.get("type").getAsString());
    }
}
