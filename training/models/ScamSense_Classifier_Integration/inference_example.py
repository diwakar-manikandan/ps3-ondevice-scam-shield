import torch
import joblib
from pathlib import Path
from executorch.runtime import Runtime

# Locate model files
BASE_DIR = Path(__file__).resolve().parent

# Load trained TF-IDF vectorizer
vectorizer = joblib.load(
    BASE_DIR / "tfidf_vectorizer.pkl"
)

# Load ExecuTorch model
runtime = Runtime.get()

program = runtime.load_program(
    str(BASE_DIR / "scam_classifier.pte")
)

method = program.load_method("forward")


def classify_sms(sms_text):

    # Step 1: TF-IDF preprocessing
    features = vectorizer.transform([sms_text])

    # Step 2: Convert to Float32 tensor
    input_tensor = torch.tensor(
        features.toarray(),
        dtype=torch.float32
    )

    # Expected shape: [1, 5000]
    assert input_tensor.shape == (1, 5000)

    print("Input shape:", input_tensor.shape)
    print("Input dtype:", input_tensor.dtype)

    # Step 3: Execute classifier
    outputs = method.execute([input_tensor])

    # Step 4: Extract raw logit
    logit = outputs[0]

    print("Output shape:", logit.shape)
    print("Raw logit:", logit.item())

    # Step 5: Apply sigmoid
    spam_score = torch.sigmoid(logit).item()

    # Step 6: Classify
    label = "SCAM" if spam_score >= 0.5 else "SAFE"

    return {
        "message": sms_text,
        "label": label,
        "spam_score": round(spam_score * 100, 4)
    }


if __name__ == "__main__":

    message = (
        "You are under digital arrest. "
        "Transfer money immediately."
    )

    result = classify_sms(message)

    print("\nClassification Result:")
    print(result)