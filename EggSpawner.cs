using UnityEngine;

public class EggSpawner : MonoBehaviour
{
    public GameObject eggPrefab;
    public Transform spawnParent;

    private float spawnRate = 1.5f;
    private float spawnTimer = 0f;

    void Start()
    {
        if (eggPrefab == null)
        {
            CreateEggPrefab();
        }
    }

    void Update()
    {
        spawnTimer -= Time.deltaTime;

        if (spawnTimer <= 0f)
        {
            SpawnEgg();
            spawnTimer = spawnRate;
        }
    }

    void SpawnEgg()
    {
        float randomX = Random.Range(-8f, 8f);
        Vector3 spawnPos = new Vector3(randomX, 6f, 0f);

        GameObject newEgg = Instantiate(eggPrefab, spawnPos, Quaternion.identity, spawnParent);

        Egg eggScript = newEgg.GetComponent<Egg>();
        eggScript.eggType = GetEggTypeForLevel();
    }

    EggType GetEggTypeForLevel()
    {
        int level = GameManager.Instance.GetCurrentLevel();

        if (level >= 4 && Random.value < 0.2f)
            return EggType.Rainbow;
        if (level >= 3 && Random.value < 0.25f)
            return EggType.Special;
        if (level >= 2 && Random.value < 0.3f)
            return EggType.Golden;

        return EggType.Normal;
    }

    void CreateEggPrefab()
    {
        GameObject egg = new GameObject("Egg");
        egg.tag = "Egg";

        SpriteRenderer spriteRenderer = egg.AddComponent<SpriteRenderer>();
        spriteRenderer.sprite = CreateCircleSprite();

        Rigidbody2D rb = egg.AddComponent<Rigidbody2D>();
        rb.isKinematic = false;
        rb.gravityScale = 0f;
        rb.constraints = RigidbodyConstraints2D.FreezeRotation;

        CircleCollider2D collider = egg.AddComponent<CircleCollider2D>();
        collider.isTrigger = true;

        egg.AddComponent<Egg>();

        eggPrefab = egg;
    }

    Sprite CreateCircleSprite()
    {
        int size = 32;
        Texture2D texture = new Texture2D(size, size, TextureFormat.RGBA32, false);

        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                float dx = x - size / 2f;
                float dy = y - size / 2f;
                float distance = Mathf.Sqrt(dx * dx + dy * dy);

                if (distance < size / 2f)
                    texture.SetPixel(x, y, Color.white);
                else
                    texture.SetPixel(x, y, Color.clear);
            }
        }

        texture.Apply();
        return Sprite.Create(texture, new Rect(0, 0, size, size), new Vector2(0.5f, 0.5f), 16);
    }

    public void UpdateSpawnRate(float newRate)
    {
        spawnRate = newRate;
    }
}
