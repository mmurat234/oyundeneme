using UnityEngine;

public enum EggType
{
    Normal,
    Golden,
    Special,
    Rainbow
}

public class Egg : MonoBehaviour
{
    public EggType eggType = EggType.Normal;
    private int points = 10;
    private float fallSpeed = 3f;
    private Rigidbody2D rb;

    void Start()
    {
        rb = GetComponent<Rigidbody2D>();
        SetupEggType();
        SetFallSpeed();
    }

    void FixedUpdate()
    {
        rb.velocity = new Vector2(0, -fallSpeed);
    }

    void SetupEggType()
    {
        switch (eggType)
        {
            case EggType.Normal:
                GetComponent<SpriteRenderer>().color = Color.white;
                points = 10;
                break;
            case EggType.Golden:
                GetComponent<SpriteRenderer>().color = Color.yellow;
                points = 25;
                break;
            case EggType.Special:
                GetComponent<SpriteRenderer>().color = new Color(1f, 0.5f, 0f);
                points = 50;
                break;
            case EggType.Rainbow:
                GetComponent<SpriteRenderer>().color = new Color(1f, 0f, 1f);
                points = 100;
                break;
        }
    }

    void SetFallSpeed()
    {
        fallSpeed = 3f + (GameManager.Instance.GetCurrentLevel() * 0.5f);
    }

    public int GetPoints()
    {
        return points;
    }

    void OnTriggerEnter2D(Collider2D collision)
    {
        if (collision.CompareTag("Ground"))
        {
            GameManager.Instance.GameOver();
            Destroy(gameObject);
        }
    }
}
