using UnityEngine;
using TMPro;

public class GameManager : MonoBehaviour
{
    public static GameManager Instance;

    public int score = 0;
    public int currentLevel = 1;
    public int eggsCollectedInLevel = 0;
    public int eggsNeededForNextLevel = 5;

    public TextMeshProUGUI scoreText;
    public TextMeshProUGUI levelText;
    public GameObject gameOverPanel;

    private float spawnRate = 1.5f;
    private float minSpawnRate = 0.3f;
    private bool gameOver = false;

    private EggSpawner eggSpawner;

    void Awake()
    {
        if (Instance == null)
            Instance = this;
        else
            Destroy(gameObject);
    }

    void Start()
    {
        eggSpawner = GetComponent<EggSpawner>();
        gameOverPanel.SetActive(false);
        UpdateUI();
    }

    void Update()
    {
        if (gameOver && Input.GetKeyDown(KeyCode.Space))
        {
            RestartGame();
        }
    }

    public void AddScore(int points)
    {
        score += points;
        eggsCollectedInLevel++;
        UpdateUI();

        if (eggsCollectedInLevel >= eggsNeededForNextLevel)
        {
            LevelUp();
        }
    }

    void LevelUp()
    {
        currentLevel++;
        eggsCollectedInLevel = 0;
        eggsNeededForNextLevel += 2;

        spawnRate = Mathf.Max(spawnRate - 0.15f, minSpawnRate);
        eggSpawner.UpdateSpawnRate(spawnRate);

        UpdateUI();
    }

    public void GameOver()
    {
        gameOver = true;
        gameOverPanel.SetActive(true);
        Time.timeScale = 0f;
    }

    void RestartGame()
    {
        Time.timeScale = 1f;
        UnityEngine.SceneManagement.SceneManager.LoadScene(0);
    }

    void UpdateUI()
    {
        if (scoreText != null)
            scoreText.text = "Skor: " + score;
        if (levelText != null)
            levelText.text = "Seviye: " + currentLevel + " (" + eggsCollectedInLevel + "/" + eggsNeededForNextLevel + ")";
    }

    public int GetCurrentLevel()
    {
        return currentLevel;
    }
}
