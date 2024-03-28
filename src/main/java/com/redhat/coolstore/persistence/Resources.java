// Update the Resources class to use Jakarta Persistence and Quarkus annotations
@Dependent
public class Resources {

    @PersistenceContext
    @Named("entityManager")
    private EntityManager em;

    public EntityManager getEm() {
        return em;
    }
}
