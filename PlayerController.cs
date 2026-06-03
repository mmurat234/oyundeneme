using UnityEngine;

public class PlayerController : MonoBehaviour
{
    public float moveSpeed = 8f;
    public float leftBound = -8f;
    public float rightBound = 8f;

    private Rigidbody2D rb;
    private float moveInput = 0f;

    void Start()
    {
        rb = GetComponent<Rigidbody2D>();
    }

    void Update()
    {
        HandleInput();
    }

    void FixedUpdate()
    {
        Move();
    }

    void HandleInput()
    {
        moveInput = 0f;

        if (Input.GetKey(KeyCode.A) || Input.GetKey(KeyCode.LeftArrow))
            moveInput = -1f;
        else if (Input.GetKey(KeyCode.D) || Input.GetKey(KeyCode.RightArrow))
            moveInput = 1f;
    }

    void Move()
    {
        Vector2 newPosition = rb.velocity;
        newPosition.x = moveInput * moveSpeed;
        rb.velocity = newPosition;

        Vector3 currentPos = transform.position;
        currentPos.x = Mathf.Clamp(currentPos.x, leftBound, rightBound);
        transform.position = currentPos;
    }

    void OnTriggerEnter2D(Collider2D collision)
    {
        if (collision.CompareTag("Egg"))
        {
            Egg egg = collision.GetComponent<Egg>();
            if (egg != null)
            {
                GameManager.Instance.AddScore(egg.GetPoints());
                Destroy(collision.gameObject);
            }
        }
    }
}
