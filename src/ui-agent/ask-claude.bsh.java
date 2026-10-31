/* MacroDroid: reads local system_prompt, prompt, file api_key.txt; writes request_body, success, response_return_code, response_output, response_text, input_tokens, output_tokens */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

String API_URL = "https://api.anthropic.com/v1/messages";
String MODEL = "claude-haiku-5-5";

int MAX_TOKENS = 16000;
int MAX_ATTEMPTS = 3;

String readApiKey() {
	File keyFile = new File(appContext.getFilesDir(), "api_key.txt");
	BufferedReader reader = new BufferedReader(new FileReader(keyFile));
	StringBuilder apiKey = new StringBuilder();

	String line;
	while ((line = reader.readLine()) != null) {
		apiKey.append(line);
	}

	reader.close();

	return apiKey.toString().replaceAll("\\s", "");
}

String buildRequestBody(String systemPrompt, String prompt) {
	JSONObject message = new JSONObject();
	message.put("role", "user");
	message.put("content", prompt);

	JSONArray messages = new JSONArray();
	messages.put(message);

	JSONObject requestBody = new JSONObject();
	requestBody.put("model", MODEL);
	requestBody.put("max_tokens", MAX_TOKENS);
	if (systemPrompt.trim().length() > 0) {
		requestBody.put("system", systemPrompt);
	}

	requestBody.put("messages", messages);
	return requestBody.toString();
}

HttpURLConnection sendRequest(String apiKey, String requestBody) {
	HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
	connection.setRequestMethod("POST");
	connection.setDoOutput(true);
	connection.setConnectTimeout(30000);
	connection.setReadTimeout(60000);
	connection.setRequestProperty("x-api-key", apiKey);
	connection.setRequestProperty("anthropic-version", "2023-06-01");
	connection.setRequestProperty("content-type", "application/json");

	OutputStream output = connection.getOutputStream();
	output.write(requestBody.getBytes("UTF-8"));
	output.close();

	return connection;
}

String readStream(InputStream stream) {
	if (stream == null) {
		return "";
	}

	BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
	StringBuilder text = new StringBuilder();
	String line;
	while ((line = reader.readLine()) != null) {
		text.append(line).append("\n");
	}

	reader.close();
	return text.toString();
}

String readResponseBody(HttpURLConnection connection, int returnCode) {
	if (returnCode >= 400) {
		return readStream(connection.getErrorStream());
	}

	return readStream(connection.getInputStream());
}

String extractText(JSONObject response) {
	JSONArray content = response.getJSONArray("content");
	StringBuilder text = new StringBuilder();

	for (int i = 0; i < content.length(); i++) {
		JSONObject block = content.getJSONObject(i);

		if (block.optString("type").equals("text")) {
			text.append(block.getString("text"));
		}
	}

	return text.toString().trim();
}

boolean isRetryable(int returnCode) {
	return returnCode == 429 || returnCode >= 500;
}

void writeOutputs(String requestBody, boolean success, int returnCode, String responseOutput, String responseText, int inputTokens, int outputTokens) {
	variableSetter.setString("request_body", requestBody);
	variableSetter.setBoolean("success", success);
	variableSetter.setInt("response_return_code", returnCode);
	variableSetter.setString("response_output", responseOutput);
	variableSetter.setString("response_text", responseText);
	variableSetter.setInt("input_tokens", inputTokens);
	variableSetter.setInt("output_tokens", outputTokens);
}

// Clear the outputs first, so a run that fails midway never leaves the previous run's values behind.
writeOutputs("", false, 0, "", "", 0, 0);

String systemPrompt = magicText.evaluateString("{" + "lv=system_prompt" + "}");
String prompt = magicText.evaluateString("{" + "lv=prompt" + "}");
String requestBody = buildRequestBody(systemPrompt, prompt);

System.out.println("systemPrompt.length() = `" + systemPrompt.length() + "`");
System.out.println("prompt = `" + prompt + "`");
System.out.println("requestBody.length() = `" + requestBody.length() + "`");
System.out.println();

boolean success = false;
int returnCode = 0;
String responseOutput = "";
String responseText = "";
int inputTokens = 0;
int outputTokens = 0;

try {
	String apiKey = readApiKey();

	for (int attempt = 1; attempt <= MAX_ATTEMPTS && !success; attempt++) {
		HttpURLConnection connection = sendRequest(apiKey, requestBody);
		returnCode = connection.getResponseCode();
		responseOutput = readResponseBody(connection, returnCode);
		connection.disconnect();

		System.out.println("attempt = `" + attempt + "`");
		System.out.println("returnCode = `" + returnCode + "`");
		System.out.println("responseOutput = `" + responseOutput + "`");
		System.out.println();

		if (returnCode == 200) {
			JSONObject response = new JSONObject(responseOutput);
			responseText = extractText(response);
			inputTokens = response.getJSONObject("usage").getInt("input_tokens");
			outputTokens = response.getJSONObject("usage").getInt("output_tokens");

			// A reply cut off at max_tokens is incomplete JSON, and asking again would cut it off the same way.
			String stopReason = response.optString("stop_reason");
			boolean isTruncated = stopReason.equals("max_tokens");
			System.out.println("stopReason = `" + stopReason + "`");
			System.out.println("isTruncated = `" + isTruncated + "`");
			System.out.println();

			if (isTruncated) {
				break;
			}

			success = responseText.length() > 0;
		} else if (!isRetryable(returnCode)) {
			break;
		}

		if (!success && attempt < MAX_ATTEMPTS) {
			Thread.sleep(2000 * attempt);
		}
	}
} catch (Exception exception) {
	responseOutput = exception.toString();
	System.out.println("exception = `" + responseOutput + "`");
	System.out.println();
}

System.out.println("success = `" + success + "`");
System.out.println("responseText = `" + responseText + "`");
System.out.println("inputTokens = `" + inputTokens + "`");
System.out.println("outputTokens = `" + outputTokens + "`");

writeOutputs(requestBody, success, returnCode, responseOutput, responseText, inputTokens, outputTokens);

