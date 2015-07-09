package com.android.cash1.activities.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.android.cash1.R;
import com.android.cash1.model.Cash1Activity;
import com.android.cash1.rest.ApiService;
import com.android.cash1.rest.RestClient;
import com.google.gson.JsonObject;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;


public class SecurityChallengeActivity extends Cash1Activity {

    private EditText mAnswerEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_challenge);

        setupActionBar();


        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        String username = sharedPrefs.getString("username", "");

        ApiService service = new RestClient().getApiService();
        service.getSecurityQuestion(username, new Callback<JsonObject>() {
            @Override
            public void success(JsonObject responseObj, Response response) {
                findViewById(R.id.spinner).setVisibility(View.GONE);
                findViewById(R.id.question).setVisibility(View.VISIBLE);

                String question = responseObj.getAsJsonPrimitive("Question").getAsString();
                if (question != null && !question.isEmpty()) {
                    TextView questionTextView = (TextView) findViewById(R.id.question);
                    questionTextView.setText(question);
                } else {
                    showIncorrectUsernameOrPasswordPopup();
                }
            }

            @Override
            public void failure(RetrofitError error) {
                error.printStackTrace();
            }
        });

        mAnswerEditText = (EditText) findViewById(R.id.answer);
    }

    public void submitAnswer(View view) {
        final SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        final boolean isEmailUsername = sharedPrefs.getBoolean("email_username", false);
        final boolean isEmailPassword = sharedPrefs.getBoolean("email_password", false);
        final boolean isTextUsername = sharedPrefs.getBoolean("text_username", false);
        final boolean isTextPassword = sharedPrefs.getBoolean("text_password", false);

        int userId = getUserId();
        String answer = mAnswerEditText.getText().toString();

        ApiService service = new RestClient().getApiService();
        service.validateSecurityAnswer(userId, answer, isEmailUsername, isEmailPassword, isTextUsername, isTextPassword, new Callback<JsonObject>() {
            @Override
            public void success(JsonObject responseObj, Response response) {
                int userId = responseObj.getAsJsonPrimitive("CustomerId").getAsInt();
                if (userId > 0) {
                    navigateToLoginScreen(isEmailUsername, isEmailPassword, isTextUsername, isTextPassword);

                    sharedPrefs.edit()
                            .putBoolean("email_username", false)
                            .putBoolean("email_password", false)
                            .putBoolean("text_username", false)
                            .putBoolean("text_password", false)
                            .apply();
                } else {
                    showValidateErrorPopup();
                }
            }

            @Override
            public void failure(RetrofitError error) {
                error.printStackTrace();
            }
        });
    }

    private void navigateToLoginScreen(boolean isEmailUsername, boolean isEmailPassword, boolean isTextUsername, boolean isTextPassword) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("isEmailUsername", isEmailUsername);
        intent.putExtra("isEmailPassword", isEmailPassword);
        intent.putExtra("isTextUsername", isTextUsername);
        intent.putExtra("isTextPassword", isTextPassword);
        startActivity(intent);
    }
}