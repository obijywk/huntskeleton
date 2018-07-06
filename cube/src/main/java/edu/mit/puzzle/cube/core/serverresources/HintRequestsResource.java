package edu.mit.puzzle.cube.core.serverresources;

import com.google.common.base.Preconditions;

import edu.mit.puzzle.cube.core.model.HintRequest;
import edu.mit.puzzle.cube.core.model.HintRequests;
import edu.mit.puzzle.cube.core.model.PostResult;
import edu.mit.puzzle.cube.core.model.Visibility;
import edu.mit.puzzle.cube.core.permissions.HintsPermission;
import edu.mit.puzzle.cube.core.permissions.PermissionAction;

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import java.util.List;

public class HintRequestsResource extends AbstractCubeResource {
    @Get
    public HintRequests handleGet() {
        List<HintRequest> hintRequests;
        String teamId = getQueryValue("teamId");
        if (teamId != null && !teamId.isEmpty()) {
            String puzzleId = getQueryValue("puzzleId");
            Preconditions.checkArgument(
                    puzzleId != null && !puzzleId.isEmpty(),
                    "puzzleId must be specified"
            );
            puzzleId = puzzleStore.getCanonicalPuzzleId(puzzleId);

            getSubject().checkPermission(
                    new HintsPermission(teamId, PermissionAction.READ)
            );
            hintRequests = hintRequestStore.getHintRequestsForTeamAndPuzzle(teamId, puzzleId);
        } else {
            getSubject().checkPermission(
                    new HintsPermission("*", PermissionAction.READ)
            );
            hintRequests = hintRequestStore.getNonTerminalHintRequests();
        }
        return HintRequests.builder().setHintRequests(hintRequests).build();
    }

    @Post
    public PostResult handlePost(HintRequest hintRequest) {
        getSubject().checkPermission(
                new HintsPermission(hintRequest.getTeamId(), PermissionAction.CREATE));

        if (hintRequest.getHintType() == null) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_BAD_REQUEST,
                    "A hintType must be specified when creating a hint request");
        }

        hintRequest = hintRequest.toBuilder()
            .setPuzzleId(puzzleStore.getCanonicalPuzzleId(hintRequest.getPuzzleId()))
            .build();

        Visibility visibility = huntStatusStore.getVisibility(
                hintRequest.getTeamId(),
                hintRequest.getPuzzleId()
        );
        if (!huntStatusStore.getVisibilityStatusSet().allowsSubmissions(visibility.getStatus())) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_BAD_REQUEST,
                    "Requesting a hint is not allowed due to puzzle visibility");
        }

        boolean success = hintRequestStore.createHintRequest(hintRequest);
        return PostResult.builder().setCreated(success).build();
    }
}
