\# ScamSense AI – Classifier Integration



\## 1. Model Details



\- Architecture: Logistic Regression

\- Framework: PyTorch

\- Runtime: ExecuTorch

\- Model file: scam\_classifier.pte

\- Model size: Approximately 20.91 KiB

\- Parameters: 5,001

\- ExecuTorch export version: 1.6.0

\- PyTorch export version: 2.15.0



\## 2. Input Specification



\- Input shape: \[1, 5000]

\- Input datatype: Float32

\- Preprocessing: TF-IDF

\- Vocabulary size: 5000

\- N-gram range: (1, 2)

\- Lowercase: True

\- Sublinear TF: True

\- Normalization: L2

\- Token pattern: (?u)\\b\\w\\w+\\b



\## 3. Preprocessing Procedure



1\. Convert the SMS to lowercase.

2\. Tokenize using the configured token pattern.

3\. Generate unigrams and bigrams.

4\. Count occurrences of vocabulary features.

5\. Apply sublinear term frequency: 1 + ln(count), for nonzero counts.

6\. Multiply each feature by its corresponding IDF weight.

7\. Apply L2 normalization. Leave an all-zero vector unchanged.

8\. Create a Float32 tensor of shape \[1, 5000].



Use vocabulary.json to identify the feature indices and idf\_weights.json for the corresponding IDF values.



\## 4. Output Specification



\- Output shape: \[1, 1]

\- Output datatype: Float32

\- Output type: Raw logit



Apply sigmoid:



score = 1 / (1 + exp(-logit))



Classification:



\- Score >= 0.5: Spam/SCAM prediction

\- Score below 0.5: Ham/SAFE prediction



The score is not a calibrated scam probability. A SAFE prediction does not guarantee that a message is safe.



\## 5. File Description



\- scam\_classifier.pte: ExecuTorch inference model.

\- vocabulary.json: TF-IDF feature-to-index mapping.

\- idf\_weights.json: IDF weights in feature-index order.

\- preprocessing\_config.json: Exact preprocessing configuration.

\- tfidf\_vectorizer.pkl: Python reference vectorizer.

\- model\_config.json: Model configuration.

\- detection\_rules.json: Hybrid detection rule configuration.

\- inference\_example.py: Python inference reference.



\## 6. Android Integration



Implement TF-IDF preprocessing in Kotlin using the JSON files. The Python PKL file is not required for Android inference.



Load scam\_classifier.pte using the ExecuTorch Android runtime.



Execute the forward method with a Float32 tensor of shape \[1, 5000].



Apply sigmoid to the returned logit.



Integrate the result into classifySms() in ChatViewModel.kt.



Verify Android runtime compatibility and numerical agreement with Python before proceeding.



\## 7. Verification Example



Input:



You are under digital arrest. Transfer money immediately.



Expected standalone ML score: Approximately 50.7976%.



Expected classifier prediction: SCAM.



Expected hybrid risk score after behavioural detection: 75/100.



\## 8. Evaluation



UCI SMS Spam Collection held-out test set:



\- Accuracy: 97.87%

\- Precision: 94.92%

\- Recall: 87.50%

\- F1-score: 91.06%



Fresh synthetic Indian message challenge:



\- Standalone ML accuracy: 70%

\- Test size: 20 messages



The UCI results measure spam classification performance, not real-world scam detection accuracy.



\## 9. Important Notes



The classifier is one component of the complete ScamSense detection system.



Final risk assessment should combine ML inference, manipulation fingerprinting and relevant threat detection rules.



Llama should explain the detected evidence rather than independently inventing threat indicators.



The Python ExecuTorch runtime has been verified. Android integration and numerical parity testing are pending.

